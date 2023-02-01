package timewheel.hierarchical.v1;

import timewheel.MyTimeoutTaskNode;

import java.util.concurrent.Executor;

public class MyHierarchicalHashedTimeWheelV1 {

    private final MyHierarchyHashedTimeWheelBucketV1[] ringBucketArray;

    /**
     * 总tick数
     * */
    private long totalTick = 0;

    /**
     * 当前时间轮所能承载的时间间隔
     * */
    private final long interval;

    /**
     * ringBuffer.length的值减1, 作为掩码计算
     * */
    private final int mask;

    /**
     * 时间轮每次转动的时间(单位：纳秒nanos)
     * (perTickTime越短，调度会更精确，但cpu开销也会越大)
     * */
    private final long perTickTime;

    /**
     * 上一层时间跨度更大的时间轮
     * */
    private MyHierarchicalHashedTimeWheelV1 overFlowWheel;

    /**
     * 用于实际执行到期任务的线程池
     * */
    private final Executor taskExecutor;

    public MyHierarchicalHashedTimeWheelV1(int ringArraySize,long perTickTime, Executor taskExecutor) {
        this.ringBucketArray = new MyHierarchyHashedTimeWheelBucketV1[ringArraySize];
        for(int i=0; i<ringArraySize; i++){
            // 初始化，填充满时间轮唤醒数组
            this.ringBucketArray[i] = new MyHierarchyHashedTimeWheelBucketV1();
        }

        this.perTickTime = perTickTime;
        this.taskExecutor = taskExecutor;
        this.interval = perTickTime * ringArraySize;
        this.mask = ringArraySize-1;
    }

    /**
     * 当前时间轮加入任务(溢出的话，则需要放到上一层的时间轮中)
     * */
    public void addTimeoutTask(long startTime, MyTimeoutTaskNode timeoutTaskNode){
        long deadline = timeoutTaskNode.getDeadline();

        // 当前时间轮所能承载的最大绝对时间为：每个tick的间隔 * 插槽数 + startTime
        long currentWheelMaxRange = this.interval + startTime;

        if(deadline < currentWheelMaxRange){
            // 当前时间轮能够承载这个任务，无需放到上一层时间轮中

            // 计算到任务超时时，应该执行多少次tick
            // (和netty里的不一样，这里的deadline是超时时间的绝对时间，所以需要先减去时间轮的startTime)
            // (netty中是生产者线程在add时事先减去了startTime，比起由worker线程统一处理效率更高，但个人觉得这里的写法会更直观)
            long totalTickWhenTimeout = (deadline - startTime) / this.perTickTime;

            // 如果传入的deadline早于当前系统时间，则totalTickWhenTimeout可能会小于当前的totalTick
            // 这种情况下，让这个任务在当前tick下就立即超时而被调度是最合理的，而不能在求余后放到一个错误的位置而等一段时间才调度（所以必须取两者的最大值）
            final long ticks = Math.max(totalTickWhenTimeout, this.totalTick); // Ensure we don't schedule for past.
            int stopIndex = (int) (ticks & mask);
            MyHierarchyHashedTimeWheelBucketV1 bucket = this.ringBucketArray[stopIndex];
            // 计算并找到应该被放置的那个bucket后，将其插入当前bucket指向的链表中
            bucket.addTimeout(timeoutTaskNode);
        }else{
            // 当前时间轮无法承载这个任务，需要放到上一层时间轮中

            // 上层时间轮不存在，创建之
            if(this.overFlowWheel == null){
                // 上层时间轮的环形数组大小保持不变，perTick是当前时间轮的整个间隔(类似低层的60秒等于上一层的1分钟)
                this.overFlowWheel = new MyHierarchicalHashedTimeWheelV1(
                    this.ringBucketArray.length, this.interval, taskExecutor);
            }

            // 加入到上一层的时间轮中(对于较大的deadline，addTimeoutTask操作可能会递归数次，放到第N层的时间轮中)
            this.overFlowWheel.addTimeoutTask(startTime,timeoutTaskNode);
        }
    }

    public void advanceClockByTick(){

        // todo
        // 1. 如果是最底层的时间轮，将当前tick下命中的bucket中的任务丢到taskExecutor中执行
        //    如果不是最底层的时间轮，将当前tick下命中的bucket中的任务交给下一层的时间轮
        //    这里转交到下一层有两种方式：一种是从上到下的转交，另一种是当做新任务一样还是从最下层的时间轮开始放，放不下再往上溢出

        // 2. 当前时间轮的总tick数满了一圈之后(totalTick / ringBucketArray.length) == 0时，推进上一层时间轮进行一次tick(如果上一层时间轮存在的话)

        this.totalTick++;
    }
}
