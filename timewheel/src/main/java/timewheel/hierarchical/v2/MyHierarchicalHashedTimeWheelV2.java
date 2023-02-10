package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executor;

public class MyHierarchicalHashedTimeWheelV2 {

    /**
     * 上层时间轮（生产者/消费者都会访问到，volatile修饰）
     * */
    private volatile MyHierarchicalHashedTimeWheelV2 overflowTimeWheel;

    /**
     * 时间轮的启动时间（单位:纳秒）
     * */
    private final long startTime;

    /**
     * 每次tick的间隔（单位:纳秒）
     * */
    private final long perTickTime;

    /**
     * 时间轮环形数组
     * */
    private final MyHierarchyHashedTimeWheelBucketV2[] ringBucketArray;

    /**
     * 用于实际执行到期任务的线程池
     * */
    private final Executor taskExecutor;

    /**
     * 时间轮的当前时间
     * */
    private long currentTime;

    /**
     * 当前时间轮的间隔(每次tick的时间 * 时间轮的大小)
     * */
    private final long interval;

    private final DelayQueue<MyHierarchyHashedTimeWheelBucketV2> bucketDelayQueue;

    public MyHierarchicalHashedTimeWheelV2(long startTime, long perTickTime, int wheelSize, Executor taskExecutor,
                                           DelayQueue<MyHierarchyHashedTimeWheelBucketV2> bucketDelayQueue) {
        // 初始化环形数组
        this.ringBucketArray = new MyHierarchyHashedTimeWheelBucketV2[wheelSize];
        for(int i=0; i<wheelSize; i++){
            this.ringBucketArray[i] = new MyHierarchyHashedTimeWheelBucketV2();
        }

        this.startTime = startTime;
        // 初始化时，当前时间为startTime
        this.currentTime = startTime - (startTime % perTickTime);
        this.perTickTime = perTickTime;
        this.taskExecutor = taskExecutor;
        this.interval = perTickTime * wheelSize;
        this.bucketDelayQueue = bucketDelayQueue;
    }

    public void addTimeoutTask(MyTimeoutTaskNode timeoutTaskNode) {
        long deadline = timeoutTaskNode.getDeadline();
        if(deadline < this.currentTime + this.perTickTime){
            // 超时时间小于1tick，直接执行
            this.taskExecutor.execute(timeoutTaskNode.getTargetTask());
        }else if(deadline < this.currentTime + this.interval){
            // 当前时间轮放的下

            // 在超时时，理论上总共需要的tick数
            long totalTick = deadline / this.perTickTime;

            // 如果传入的deadline早于当前系统时间，则totalTickWhenTimeout可能会小于当前的totalTick
            // 这种情况下，让这个任务在当前tick下就立即超时而被调度是最合理的，而不能在求余后放到一个错误的位置而等一段时间才调度（所以必须取两者的最大值）
            // 如果能限制环形数组的长度为2的幂，则可以改为ticks & mask，位运算效率更高
            int stopIndex = (int) (totalTick % this.ringBucketArray.length);

            MyHierarchyHashedTimeWheelBucketV2 bucket = this.ringBucketArray[stopIndex];
            // 计算并找到应该被放置的那个bucket后，将其插入当前bucket指向的链表中
            bucket.addTimeout(timeoutTaskNode);

            // deadline先除以this.perTickTime再乘以this.perTickTime,可以保证放在同一个插槽下的任务，expiration都是一样的
            long expiration = totalTick * this.perTickTime;
            boolean isNewRound = bucket.setExpiration(expiration);
            if(isNewRound){
                this.bucketDelayQueue.offer(bucket);
//                System.out.println(bucketDelayQueue);
            }
        }else{
            // 当前时间轮放不下
            if(this.overflowTimeWheel == null){
                createOverflowWheel();
            }

            // 加入到上层的时间轮中(较大的deadline会递归多次)
            this.overflowTimeWheel.addTimeoutTask(timeoutTaskNode);
        }
    }

    /**
     * 推进当前时间轮的时钟
     * 举个例子：假设当前时间轮的当前时间是第10分钟，perTickTime是1分钟，
     * 1.如果expiration是第10分钟第1秒，则不用推动当前时间
     * 2.如果expiration是第11分钟第0秒，则需要推动当前时间
     * */
    public void advanceClockByTick(long expiration){
        // 只会在tick推进时才会被调用，参数expiration可以认为是当前时间轮的系统时间
        if(expiration >= this.currentTime + this.perTickTime){
            // 超过了1tick，则需要推进当前时间轮 (始终保持当前时间是perTickTime的整数倍，逻辑上的totalTick)
            this.currentTime = expiration - (expiration % this.perTickTime);
            if(this.overflowTimeWheel != null){
                // 如果上层时间轮存在，则递归的继续推进
                this.overflowTimeWheel.advanceClockByTick(expiration);
            }
        }
    }

    private synchronized void createOverflowWheel(){
        if(this.overflowTimeWheel == null){
            // 创建上层时间轮，上层时间轮的perTickTime = 当前时间轮的interval
            this.overflowTimeWheel = new MyHierarchicalHashedTimeWheelV2(
                this.currentTime, this.interval, this.ringBucketArray.length, this.taskExecutor, this.bucketDelayQueue);
        }
    }
}