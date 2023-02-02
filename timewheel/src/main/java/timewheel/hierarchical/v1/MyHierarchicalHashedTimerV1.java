package timewheel.hierarchical.v1;

import timewheel.MyTimeoutTaskNode;

import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * 层次时间轮，会存在空转问题
 * */
public class MyHierarchicalHashedTimerV1 {

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
     * 总tick数
     * */
    private long totalTick = 0;

    /**
     * 待处理任务的队列
     * (多外部生产者写入，时间轮内的单worker消费者读取，所以netty的实现里使用了效率更高的MpscQueue，Mpsc即MultiProducerSingleConsumer)
     * */
    private final Queue<MyTimeoutTaskNode> unProcessTaskQueue = new LinkedBlockingDeque<>();

    /**
     * timer持有的最低层的时间轮
     * */
    final MyHierarchicalHashedTimeWheelV1 lowestTimeWheel;

    /**
     * 构造函数
     * */
    public MyHierarchicalHashedTimerV1(int ringArraySize, long perTickTime, Executor taskExecutor) {
        this.perTickTime = perTickTime;
        this.taskExecutor = taskExecutor;

        // 初始化最底层的时间轮
        this.lowestTimeWheel = new MyHierarchicalHashedTimeWheelV1(ringArraySize,perTickTime,taskExecutor,true);
    }

    /**
     * 启动worker线程等初始化操作，必须执行完成后才能正常工作
     * (简单起见，和netty不一样不是等任务被创建时才懒加载的，必须提前启动)
     * */
    public void startTimeWheel(){
        // 启动worker线程
        new Thread(new MyHierarchicalHashedTimerV1.Worker()).start();
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
            MyHierarchicalHashedTimerV1.this.startTime = System.nanoTime();

            // 简单起见，不考虑优雅启动和暂停的逻辑
            while (true){
                // 等待perTick
                waitForNextTick();

                // 在捞取当前tick下需要处理的bucket前，先将加入到队列中的任务转移到时间轮中(可能包含在当前tick下就要处理的任务)
                // 层级时间轮内部会做进一步的分配(放不下的话就溢出到更上一层的时间轮)
                transferTaskToTimeWheel();

                // 推进时间轮(层级时间轮内部满了一圈就会进一步的推进更上一层的时间轮)
                MyHierarchicalHashedTimerV1.this.lowestTimeWheel.advanceClockByTick(
                    (taskNode)->
                        // 参考kafka的写法，避免Timer里的一些属性被传到各个bucket里面
                        MyHierarchicalHashedTimerV1.this.lowestTimeWheel
                            .addTimeoutTask(MyHierarchicalHashedTimerV1.this.startTime, taskNode)
                );

                // 循环tick一次，总tick数自增1
                MyHierarchicalHashedTimerV1.this.totalTick++;
            }
        }

        /**
         * per tick时钟跳动，基于Thread.sleep
         * */
        private void waitForNextTick(){
            // 由于Thread.sleep并不是绝对精确的被唤醒，所以只能通过(('总的tick数+1' * '每次tick的间隔') + '时间轮启动时间')来计算精确的下一次tick时间
            // 而不能简单的Thread.sleep(每次tick的间隔)

            long nextTickTime = (MyHierarchicalHashedTimerV1.this.totalTick + 1) * MyHierarchicalHashedTimerV1.this.perTickTime
                + MyHierarchicalHashedTimerV1.this.startTime;

            // 因为nextTickTime是纳秒，sleep需要的是毫秒，需要保证纳秒数过小时，导致直接计算出来的毫秒数为0
            // 因此(‘实际休眠的纳秒数’+999999)/1000000,保证了纳秒转毫秒时，至少会是1毫秒，而不会出现sleep(0毫秒)令cpu空转
            long needSleepTime = (nextTickTime - System.nanoTime() + 999999) / 1000000;
//            System.out.println("waitForNextTick needSleepTime=" + needSleepTime);
            try {
                // 比起netty，忽略了一些处理特殊场景bug的逻辑
                Thread.sleep(needSleepTime);
            } catch (InterruptedException ignored) {

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
                MyTimeoutTaskNode timeoutTaskNode = MyHierarchicalHashedTimerV1.this.unProcessTaskQueue.poll();
                if (timeoutTaskNode == null) {
                    // 队列为空了，直接结束
                    return;
                }

                // 层级时间轮内部会做进一步的分配(放不下的话就溢出到更上一层的时间轮)
                MyHierarchicalHashedTimerV1.this.lowestTimeWheel.addTimeoutTask(
                    MyHierarchicalHashedTimerV1.this.startTime, timeoutTaskNode);
            }
        }
    }
}
