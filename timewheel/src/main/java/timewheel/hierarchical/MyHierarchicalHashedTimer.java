package timewheel.hierarchical;

import timewheel.MyHashedTimeWheel;

import java.sql.Time;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

/**
 * 参考kafka实现的多层时间轮
 * */
public class MyHierarchicalHashedTimer {

    /**
     * 用于避免空推进的延迟队列，保存时间轮中每个插槽的链表
     */
    private DelayQueue<MyHierarchyHashedTimeWheelBucket> delayQueue = new DelayQueue<>();

    private MyHierarchicalHashedTimeWheel timeWheel;

    /**
     * 世间轮启动时的具体时间戳(单位：纳秒nanos)
     * */
    private long startTime;

    /**
     * 时间轮每次转动的时间(单位：纳秒nanos)
     * (perTickTime越短，调度会更精确，但cpu开销也会越大)
     * */
    private long perTickTime;

    /**
     * 总tick数
     * */
    private long totalTick = 0;

    private int ringBucketArraySize;


    public void newTimeoutTask(Runnable task, long delayTime, TimeUnit timeUnit) {
        this.timeWheel.newTimeoutTask(task, delayTime, timeUnit);
    }

    private final class Worker implements Runnable {
        @Override
        public void run() {
            while(true){
                // 最大等待的时间，就是一个时间轮完整走完一圈的时间
                // 即时间轮的大小ringBucketArraySize * 每次tick的时间perTickTime
                long maxWaitTime = MyHierarchicalHashedTimer.this.ringBucketArraySize * MyHierarchicalHashedTimer.this.perTickTime;
                MyHierarchyHashedTimeWheelBucket myHierarchyHashedTimeWheelBucket = delayQueue.poll(maxWaitTime, TimeUnit.NANOSECONDS);
            }
        }
    }
}