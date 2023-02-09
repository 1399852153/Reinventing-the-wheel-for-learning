package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;

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

    public MyHierarchicalHashedTimeWheelV2(long startTime, long perTickTime, int wheelSize, Executor taskExecutor) {
        // 初始化环形数组
        this.ringBucketArray = new MyHierarchyHashedTimeWheelBucketV2[wheelSize];
        for(int i=0; i<wheelSize; i++){
            this.ringBucketArray[i] = new MyHierarchyHashedTimeWheelBucketV2();
        }

        this.startTime = startTime;
        // 初始化时，当前时间为startTime
        this.currentTime = startTime;
        this.perTickTime = perTickTime;
        this.taskExecutor = taskExecutor;
        this.interval = perTickTime * wheelSize;
    }

    public void addTimeoutTask(MyTimeoutTaskNode timeoutTaskNode) {
        long deadline = timeoutTaskNode.getDeadline();
        if(deadline < this.currentTime + this.perTickTime){
            // 超时时间小于1tick，直接执行
            this.taskExecutor.execute(timeoutTaskNode.getTargetTask());
        }else if(deadline < this.currentTime + this.interval){
            // 当前时间轮放的下
        }else{
            // 当前时间轮放不下
        }
    }
}