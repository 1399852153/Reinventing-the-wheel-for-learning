package timewheel;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class MyHashedTimeWheel {

    /**
     * 环形数组
     * */
    private MyHashedTimeWheelBucket[] ringBuffer;

    /**
     * ringBuffer.length的值减1, 作为掩码计算
     * */
    private int mask;

    /**
     * 世间轮启动时的具体时间戳
     * */
    private long startTime;

    /**
     * 待处理任务的队列
     * (多外部生产者写入，时间轮内的单worker消费者读取，所以netty的实现里使用了效率更高的MpscQueue，Mpsc即MultiProducerSingleConsumer)
     * */
    private final Queue<MyTimeoutTaskNode> unProcessTaskQueue = new ConcurrentLinkedQueue<>();

    /**
     * 用于实际执行到期任务的线程池
     * */
    private Executor taskExecutor;


    private void newTimeoutTask(Runnable task, long delayTime, TimeUnit timeUnit){
        long deadline = System.nanoTime() + timeUnit.toNanos(delayTime);


    }
}
