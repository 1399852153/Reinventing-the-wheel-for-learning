package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;
import timewheel.util.PrintDateUtil;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
    private volatile MyHierarchicalHashedTimeWheelV2 overFlowWheel;

    /**
     * 用于实际执行到期任务的线程池
     * */
    private final Executor taskExecutor;

    /**
     * 保存bucket的延迟队列，用于解决时间轮空转的问题
     */
    private final DelayQueue<MyHierarchyHashedTimeWheelBucketV2> delayQueue;

    /**
     * 当前时间轮的绝对时间
     * */
    private long currentTime;

    private final long startTime;

    public MyHierarchicalHashedTimeWheelV2(int ringArraySize,long startTime, long currentTime, long perTickTime,
                                           Executor taskExecutor, int level, DelayQueue<MyHierarchyHashedTimeWheelBucketV2> delayQueue) {
        this.ringBucketArray = new MyHierarchyHashedTimeWheelBucketV2[ringArraySize];
        for(int i=0; i<ringArraySize; i++){
            // 初始化，填充满时间轮唤醒数组
            this.ringBucketArray[i] = new MyHierarchyHashedTimeWheelBucketV2(level,i);
        }

        this.startTime = startTime;
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
    public void addTimeoutTask(MyTimeoutTaskNode timeoutTaskNode){
        long deadline = timeoutTaskNode.getDeadline();

        if(deadline < this.currentTime + this.perTickTime){
//            System.out.println("deadline太小了,连1tick都不到就要被执行"
//                + " deadline=" + PrintDateUtil.parseDate(deadline)
//                + " currentTime=" + PrintDateUtil.parseDate(currentTime)
//                + " perTickTime=" + TimeUnit.NANOSECONDS.toMillis(perTickTime) + "ms"
//                + " interval=" + TimeUnit.NANOSECONDS.toMillis(interval) + "ms"
//            );

            // deadline太小了,连1tick都不到就要被执行
            // 直接去执行即可
            this.taskExecutor.execute(timeoutTaskNode.getTargetTask());
        }else if(deadline < currentTime + this.interval){
            // 当前时间轮放得下，找到对应的位置

            long remainTick = (deadline - startTime) / this.perTickTime;
            // 计算应该被放到当前时间轮的哪一个插槽中
            int targetBucketIndex = (int) (remainTick % this.ringBucketArray.length);

            MyHierarchyHashedTimeWheelBucketV2 targetBucket = this.ringBucketArray[targetBucketIndex];
            // 将任务放到对应插槽中
            targetBucket.addTimeout(timeoutTaskNode);

            // 设置当前插槽的过期时间
            // 由于对perTickTime做了除法，能保证只要放在同一个桶内的所有任务节点，即使deadline不同，最后算出来的expiration是一样的
            long expiration = deadline / this.perTickTime * this.perTickTime;

//            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
//            System.out.println("当前时间轮放得下，找到对应的位置 level=" + this.level
//                + " deadline=" + simpleDateFormat.format(new Date(TimeUnit.NANOSECONDS.toMillis(deadline)))
//                + " currentTime=" + simpleDateFormat.format(new Date(TimeUnit.NANOSECONDS.toMillis(currentTime)))
//                + " perTickTime=" + TimeUnit.NANOSECONDS.toMillis(perTickTime) + "ms"
//                + " interval=" + TimeUnit.NANOSECONDS.toMillis(interval) + "ms"
//                + " remainTick=" + remainTick
//                + " targetBucketIndex=" + targetBucketIndex
//                + " expiration=" + simpleDateFormat.format(new Date(TimeUnit.NANOSECONDS.toMillis(expiration)))
//            );

            boolean isNewRound = targetBucket.setExpiration(expiration);
            if(isNewRound){
                // 之前的expiration和参数不一致，说明是新的一轮（之前bucket里的数据已经被取出来处理掉了），需要将当前bucket放入timer的延迟队列中
                this.delayQueue.offer(targetBucket);
            }
        }else{
//            System.out.println("超过了当前时间轮的承载范围, 加入到上层时间轮"
//                + " deadline=" + PrintDateUtil.parseDate(deadline)
//                + " currentTime=" + PrintDateUtil.parseDate(currentTime)
//                + " perTickTime=" + TimeUnit.NANOSECONDS.toMillis(perTickTime) + "ms"
//                + " interval=" + TimeUnit.NANOSECONDS.toMillis(interval) + "ms"
//            );

            // 超过了当前时间轮的承载范围, 加入到上层时间轮

            // 上层时间轮不存在，创建之
            MyHierarchicalHashedTimeWheelV2 overflowWheel = getOverflowWheel();

            // 加入到上一层的时间轮中(对于较大的deadline，addTimeoutTask操作可能会递归数次，放到第N层的时间轮中)
            overflowWheel.addTimeoutTask(timeoutTaskNode);
        }
    }

    /**
     * 和v1版本不同，是在最近的bucket被拉取时才执行的
     * 所以要基于bucket中设置的超时时间expiration来更新当前时间
     * */
    public void advanceClockByTick(MyHierarchyHashedTimeWheelBucketV2 bucket){
        long expiration = bucket.getExpiration();

//        System.out.println("===============================");
//        System.out.println("advanceClockByTick" +
//            " expiration=" + PrintDateUtil.parseDate(expiration) +
//            " currentTime=" + PrintDateUtil.parseDate(currentTime) +
//            " perTickTime=" + TimeUnit.NANOSECONDS.toMillis(this.perTickTime) +
//            " level=" + level
//        );
//        System.out.println("===============================");

        // bucket的expiration可以理解为当前时间，如果当前时间比当前时间轮的原有时间 + 1tick大，说明需要推进当前时间轮的刻度了
        if(expiration >= this.currentTime + this.perTickTime){
//            System.out.println("更新当前时间轮的刻度 level=" + level);
            // 更新当前时间轮的刻度
            this.currentTime = expiration - (expiration % this.perTickTime);

            if (this.overFlowWheel != null) {
                // 计算当前是否需要推进上一层的时间轮
                this.overFlowWheel.advanceClockByTick(bucket);
            }
        }else{
//            System.out.println("不更新当前时间轮的刻度 level=" + level);
        }
    }

    private MyHierarchicalHashedTimeWheelV2 getOverflowWheel() {
        if (this.overFlowWheel == null) {
            synchronized (this) {
                if(this.overFlowWheel == null){
                    // 上层时间轮的环形数组大小保持不变，perTick是当前时间轮的整个间隔(类似低层的60秒等于上一层的1分钟)
                    this.overFlowWheel = new MyHierarchicalHashedTimeWheelV2(
                        this.ringBucketArray.length,this.startTime, this.currentTime, this.interval, this.taskExecutor,this.level+1,this.delayQueue);
                }
            }
        }
        return this.overFlowWheel;
    }

    @Override
    public String toString() {
        return "MyHierarchicalHashedTimeWheelV2{" +
            "ringBucketArray=" + Arrays.toString(ringBucketArray) + " " +
            "level=" + this.level +
            '}';
    }
}
