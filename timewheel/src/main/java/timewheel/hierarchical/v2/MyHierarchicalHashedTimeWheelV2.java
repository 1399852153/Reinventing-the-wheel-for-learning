package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;

import java.util.Arrays;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class MyHierarchicalHashedTimeWheelV2 {

    private final MyHierarchyHashedTimeWheelBucketV2[] ringBucketArray;

    /**
     * 时间轮每次转动的时间(单位：纳秒nanos)
     * (perTickTime越短，调度会更精确，但cpu开销也会越大)
     * */
    private final long perTickTime;

    /**
     * 当前时间轮所能承载的时间间隔
     * */
    private final long interval;

    private final int level;

    /**
     * 上一层时间跨度更大的时间轮
     * */
    private MyHierarchicalHashedTimeWheelV2 overFlowWheel;

    /**
     * 用于实际执行到期任务的线程池
     * */
    private final Executor taskExecutor;

    /**
     * 保存bucket的延迟队列，用于解决时间轮空转的问题
     */
    private DelayQueue<MyHierarchyHashedTimeWheelBucketV2> delayQueue;

    /**
     * 当前时间轮的绝对时间
     * */
    private long currentTime;

    public MyHierarchicalHashedTimeWheelV2(int ringArraySize, long currentTime, long perTickTime,
                                           Executor taskExecutor, int level, DelayQueue<MyHierarchyHashedTimeWheelBucketV2> delayQueue) {
        this.ringBucketArray = new MyHierarchyHashedTimeWheelBucketV2[ringArraySize];
        for(int i=0; i<ringArraySize; i++){
            // 初始化，填充满时间轮唤醒数组
            this.ringBucketArray[i] = new MyHierarchyHashedTimeWheelBucketV2();
        }

        this.currentTime = currentTime;
        this.taskExecutor = taskExecutor;
        this.perTickTime = perTickTime;
        this.interval = perTickTime * ringArraySize;
        this.level = level;
        this.delayQueue = delayQueue;
    }

    /**
     * 当前时间轮加入任务(溢出的话，则需要放到上一层的时间轮中)
     * */
    public void addTimeoutTask(long currentTime, MyTimeoutTaskNode timeoutTaskNode){
        long deadline = timeoutTaskNode.getDeadline();

        if(deadline < currentTime){
            // deadline太小了，已经超时了
            // 直接去执行即可
            this.taskExecutor.execute(timeoutTaskNode.getTargetTask());
        }else if(deadline > currentTime + this.interval){
            // 超过了当前时间轮的承载范围, 加入到上层时间轮

            // 上层时间轮不存在，创建之
            if(this.overFlowWheel == null){
                // 上层时间轮的环形数组大小保持不变，perTick是当前时间轮的整个间隔(类似低层的60秒等于上一层的1分钟)
                this.overFlowWheel = new MyHierarchicalHashedTimeWheelV2(
                    this.ringBucketArray.length, this.currentTime, this.interval, this.taskExecutor,this.level+1,this.delayQueue);
            }

            // 加入到上一层的时间轮中(对于较大的deadline，addTimeoutTask操作可能会递归数次，放到第N层的时间轮中)
            this.overFlowWheel.addTimeoutTask(this.currentTime,timeoutTaskNode);
        }else{
            // 当前时间轮放得下，找到对应的位置

            // 计算逻辑上的总tick值
            long totalTick = deadline / this.perTickTime;
            // 计算应该被放到当前时间轮的哪一个插槽中
            int targetBucketIndex = (int) (totalTick % this.ringBucketArray.length);

            MyHierarchyHashedTimeWheelBucketV2 targetBucket = this.ringBucketArray[targetBucketIndex];
            // 将任务放到对应插槽中
            targetBucket.addTimeout(timeoutTaskNode);

            // 设置当前插槽的过期时间
            // 由于对perTickTime做了除法，能保证只要放在同一个桶内的所有任务节点，即使deadline不同，最后算出来的expiration是一样的
            long expiration = totalTick * this.perTickTime;
            boolean isNewRound = targetBucket.setExpiration(expiration);
            if(isNewRound){
                // 之前的expiration和参数不一致，说明是新的一轮（之前bucket里的数据已经被取出来处理掉了），需要将当前bucket放入timer的延迟队列中
                this.delayQueue.offer(targetBucket);
            }
        }
    }

    public void advanceClockByTick(MyHierarchyHashedTimeWheelBucketV2 bucket,Consumer<MyTimeoutTaskNode> flushInLowerWheelFn){
        if(this.level == 0){
            // 如果是最底层的时间轮，将当前tick下命中的bucket中的任务丢到taskExecutor中执行
            bucket.expireTimeoutTask(this.taskExecutor);
        }else{
            // 如果不是最底层的时间轮，将当前tick下命中的bucket中的任务交给下一层的时间轮
            // 这里转交到下一层有两种方式：第一种是从上到下的转交，另一种是当做新任务一样还是从最下层的时间轮开始放，放不下再往上溢出
            // 选用后一种逻辑，最大的复用已有的创建新任务的逻辑，会好理解一点
            bucket.flush(flushInLowerWheelFn);
        }

        // 计算当前是否需要推进上一层的时间轮
        this.overFlowWheel.advanceClockByTick(bucket,flushInLowerWheelFn);
    }

    @Override
    public String toString() {
        return "MyHierarchicalHashedTimeWheelV2{" +
            "ringBucketArray=" + Arrays.toString(ringBucketArray) + " " +
            "level=" + this.level +
            '}';
    }
}
