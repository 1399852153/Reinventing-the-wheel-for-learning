package timewheel.hierarchical;

import timewheel.MyHashedTimeWheelBucket;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class MyHierarchicalHashedTimeWheel {

    /**
     * 环形数组
     * */
    private MyHierarchyHashedTimeWheelBucket[] ringBucketArray;

    /**
     * ringBuffer.length的值减1, 作为掩码计算
     * */
    private int mask;

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

    /**
     * 用于实际执行到期任务的线程池
     * */
    private Executor taskExecutor;

    /**
     * 用于避免空推进的延迟队列，保存时间轮中每个插槽的链表
     * */
    private DelayQueue<MyHierarchyHashedTimeWheelBucket> delayQueue = new DelayQueue<>();

    /**
     * 上一层的时间轮
     * */
    private MyHierarchicalHashedTimeWheel overflowWheel;

    public void newTimeoutTask(Runnable task, long delayTime, TimeUnit timeUnit){
        // 判断当前时间轮能否承载任务，如果时间范围不对，就需要放到上一层时间轮里面
    }

}
