package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;

import java.util.Queue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * 层次时间轮，会存在空转问题
 * */
public class MyHierarchicalHashedTimerV2 {

    /**
     * 用于实际执行到期任务的线程池
     * */
    private final Executor taskExecutor;

    /**
     * 世间轮启动时的具体时间戳(单位：纳秒nanos)
     * */
    private long startTime;

    /**
     * 时间轮每次转动的时间(单位：纳秒nanos)
     * (perTickTime越短，调度会更精确，但cpu开销也会越大)
     * */
    private final long perTickTime;

    /**
     * 待处理任务的队列
     * (多外部生产者写入，时间轮内的单worker消费者读取，所以netty的实现里使用了效率更高的MpscQueue，Mpsc即MultiProducerSingleConsumer)
     * */
    private final Queue<MyTimeoutTaskNode> unProcessTaskQueue = new LinkedBlockingDeque<>();

    /**
     * timer持有的最低层的时间轮
     * */
    final MyHierarchicalHashedTimeWheelV2 lowestTimeWheel;

    /**
     * 保存bucket的延迟队列，用于解决时间轮空转的问题
     */
    private DelayQueue<MyHierarchyHashedTimeWheelBucketV2> delayQueue = new DelayQueue<>();

    /**
     * 时间轮中的环形数组的大小
     * */
    private final int ringArraySize;

    /**
     * 当前时间轮绝对时间
     * */
    private long currentTime;

    /**
     * 构造函数
     * */
    public MyHierarchicalHashedTimerV2(int ringArraySize, long perTickTime, Executor taskExecutor) {
        this.ringArraySize = ringArraySize;
        this.perTickTime = perTickTime;
        this.taskExecutor = taskExecutor;

        this.currentTime = System.nanoTime();

        // 初始化最底层的时间轮
        this.lowestTimeWheel = new MyHierarchicalHashedTimeWheelV2(ringArraySize,currentTime,perTickTime,taskExecutor,0,this.delayQueue);
    }

    /**
     * 启动worker线程等初始化操作，必须执行完成后才能正常工作
     * (简单起见，和netty不一样不是等任务被创建时才懒加载的，必须提前启动)
     * */
    public void startTimeWheel(){
        // 启动worker线程
        new Thread(new MyHierarchicalHashedTimerV2.Worker()).start();
    }

    public void newTimeoutTask(Runnable task, long delayTime, TimeUnit timeUnit){
        long deadline = System.nanoTime() + timeUnit.toNanos(delayTime);

        // Guard against overflow.
        if (delayTime > 0 && deadline < 0) {
            deadline = Long.MAX_VALUE;
        }

        MyTimeoutTaskNode newTimeoutTaskNode = new MyTimeoutTaskNode();
        newTimeoutTaskNode.setTargetTask(task);
        newTimeoutTaskNode.setDeadline(deadline);

        this.unProcessTaskQueue.add(newTimeoutTaskNode);
    }

    private final class Worker implements Runnable{

        @Override
        public void run() {
            MyHierarchicalHashedTimerV2.this.startTime = System.nanoTime();

            // 简单起见，不考虑优雅启动和暂停的逻辑
            while (true){
                // 将加入到队列中的任务转移到时间轮中，层级时间轮内部会做进一步的分配(放不下的话就溢出到更上一层的时间轮)
                transferTaskToTimeWheel();

                MyHierarchyHashedTimeWheelBucketV2 bucketV2 = waitForNextTick();

                // bucket可能为null，因为延迟队列设置了最大超时时间
                if(bucketV2 != null){
                    // 推进时间轮(层级时间轮内部满了一圈就会进一步的推进更上一层的时间轮)
                    // 参考kafka的写法，避免Timer里的一些属性被传到各个bucket里面
                    MyHierarchicalHashedTimerV2.this.lowestTimeWheel.advanceClockByTick(
                        bucketV2, MyHierarchicalHashedTimerV2.this.lowestTimeWheel::addTimeoutTask);
                }
            }
        }

        private MyHierarchyHashedTimeWheelBucketV2 waitForNextTick(){
            try {
                // 最大等待的时间长度，即当前最底层时间轮走完一整圈的时间
                long maxWaitTime = MyHierarchicalHashedTimerV2.this.perTickTime * MyHierarchicalHashedTimerV2.this.ringArraySize;
                long maxWaitTimeMills = TimeUnit.NANOSECONDS.toMillis(maxWaitTime);

                // 获得最近的非空的bucket，进行相应处理(解决时间轮空转问题)
                return delayQueue.poll(maxWaitTimeMills, TimeUnit.MILLISECONDS);
            }catch (InterruptedException e){
                // 简单起见，不解决中断异常
                throw new RuntimeException(e);
            }
        }

        /**
         * 加入到队列中的任务转移到时间轮中
         * */
        private void transferTaskToTimeWheel() {
            // 为了避免worker线程在一次循环中处理太多的任务，所以直接限制了一个最大值100000
            // 如果真的有这么多，就等到下次tick循环的时候再去做。
            // 因为这个操作是cpu密集型的，处理太多的话，可能导致无法在一个短的tick周期内完成一次循环
            for (int i = 0; i < 100000; i++) {
                MyTimeoutTaskNode timeoutTaskNode = MyHierarchicalHashedTimerV2.this.unProcessTaskQueue.poll();
                if (timeoutTaskNode == null) {
                    // 队列为空了，直接结束
                    return;
                }

                // 层级时间轮内部会做进一步的分配(放不下的话就溢出到更上一层的时间轮)
                MyHierarchicalHashedTimerV2.this.lowestTimeWheel.addTimeoutTask(timeoutTaskNode);
            }
        }
    }
}
