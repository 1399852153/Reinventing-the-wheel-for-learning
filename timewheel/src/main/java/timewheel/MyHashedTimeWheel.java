package timewheel;

import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * 参考netty实现的单层时间轮
 * */
public class MyHashedTimeWheel {

    /**
     * 环形数组
     * */
    private final MyHashedTimeWheelBucket[] ringBucketArray;

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
     * 用于实际执行到期任务的线程池
     * */
    private final Executor taskExecutor;

    /**
     * 构造函数
     * */
    public MyHashedTimeWheel(int ringArraySize, long perTickTime, Executor taskExecutor) {
        this.ringBucketArray = new MyHashedTimeWheelBucket[ringArraySize];
        for(int i=0; i<ringArraySize; i++){
            // 初始化，填充满时间轮唤醒数组
            this.ringBucketArray[i] = new MyHashedTimeWheelBucket();
        }

        this.perTickTime = perTickTime;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 启动worker线程等初始化操作，必须执行完成后才能正常工作
     * (简单起见，和netty不一样不是等任务被创建时才懒加载的，必须提前启动)
     * */
    public void startTimeWheel(){
        // 启动worker线程
        new Thread(new Worker()).start();
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

        unProcessTaskQueue.add(newTimeoutTaskNode);
    }

    private final class Worker implements Runnable{

        @Override
        public void run() {
            MyHashedTimeWheel.this.startTime = System.nanoTime();

            // 简单起见，不考虑优雅启动和暂停的逻辑
            while (true){
                // 等待perTick
                waitForNextTick();

                // 在捞取当前tick下需要处理的bucket前，先将加入到队列中的任务转移到环形数组中(可能包含在当前tick下就要处理的任务)
                transferTaskToBuckets();

                // 基于总tick数，对环形数组的长度取模，计算出当前tick下需要处理的bucket桶的下标
                int idx = (int) (MyHashedTimeWheel.this.totalTick % MyHashedTimeWheel.this.ringBucketArray.length);
                MyHashedTimeWheelBucket bucket = MyHashedTimeWheel.this.ringBucketArray[idx];
                // 处理当前插槽内的任务(遍历链表中的所有任务，round全部减一，如果减为负数了则说明这个任务超时到期了，将其从链表中移除后并交给线程池执行指定的任务)
                bucket.expireTimeoutTask(MyHashedTimeWheel.this.taskExecutor);
                // 循环tick一次，总tick数自增1
                MyHashedTimeWheel.this.totalTick++;
            }
        }

        /**
         * per tick时钟跳动，基于Thread.sleep
         * */
        private void waitForNextTick(){
            // 由于Thread.sleep并不是绝对精确的被唤醒，所以只能通过(('总的tick数+1' * '每次tick的间隔') + '时间轮启动时间')来计算精确的下一次tick时间
            // 而不能简单的Thread.sleep(每次tick的间隔)

            long nextTickTime = (MyHashedTimeWheel.this.totalTick + 1) * MyHashedTimeWheel.this.perTickTime
                            + MyHashedTimeWheel.this.startTime;

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

        private void transferTaskToBuckets() {
            // 为了避免worker线程在一次循环中处理太多的任务，所以直接限制了一个最大值100000
            // 如果真的有这么多，就等到下次tick循环的时候再去做。
            // 因为这个操作是cpu密集型的，处理太多的话，可能导致无法在一个短的tick周期内完成一次循环
            for (int i = 0; i < 100000; i++) {
                MyTimeoutTaskNode timeoutTaskNode = MyHashedTimeWheel.this.unProcessTaskQueue.poll();
                if (timeoutTaskNode == null) {
                    // 队列为空了，直接结束
                    return;
                }

                // 计算到任务超时时，应该执行多少次tick
                // (和netty里的不一样，这里的deadline是超时时间的绝对时间，所以需要先减去时间轮的startTime)
                // (netty中是生产者线程在add时事先减去了startTime，比起由worker线程统一处理效率更高，但个人觉得这里的写法会更直观)
                long totalTickWhenTimeout = (timeoutTaskNode.getDeadline() - MyHashedTimeWheel.this.startTime) / MyHashedTimeWheel.this.perTickTime;
                // 减去当前时间轮已经进行过的tick数量
                long remainingTickWhenTimeout = (totalTickWhenTimeout - MyHashedTimeWheel.this.totalTick);
                // 因为一次时间轮旋转会经过ringBucketArray.length次tick，所以求个余数
                long remainingRounds = remainingTickWhenTimeout / MyHashedTimeWheel.this.ringBucketArray.length;
                // 计算出当前任务需要转多少圈之后才会超时
                timeoutTaskNode.setRounds(remainingRounds);

                // 如果传入的deadline早于当前系统时间，则totalTickWhenTimeout可能会小于当前的totalTick
                // 这种情况下，让这个任务在当前tick下就立即超时而被调度是最合理的，而不能在求余后放到一个错误的位置而等一段时间才调度（所以必须取两者的最大值）
                final long ticks = Math.max(totalTickWhenTimeout, MyHashedTimeWheel.this.totalTick); // Ensure we don't schedule for past.
                // 如果能限制环形数组的长度为2的幂，则可以改为ticks & mask，位运算效率更高
                int stopIndex = (int) (ticks % MyHashedTimeWheel.this.ringBucketArray.length);
                MyHashedTimeWheelBucket bucket = MyHashedTimeWheel.this.ringBucketArray[stopIndex];
                // 计算并找到应该被放置的那个bucket后，将其插入当前bucket指向的链表中
                bucket.addTimeout(timeoutTaskNode);
            }
        }
    }
}
