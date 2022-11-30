package threadpool.blog;

import threadpool.MyRejectedExecutionHandler;
import threadpool.MyScheduledExecutorService;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class MyScheduledThreadPoolExecutor extends MyThreadPoolExecutorV2 implements MyScheduledExecutorService {

    /**
     * 单调自增发号器，为每一个新创建的ScheduledFutureTask设置一个唯一的序列号
     * */
    private static final AtomicLong sequencer = new AtomicLong();

    /**
     * 取消任务时，是否需要将其从延迟队列中移除掉
     * True if ScheduledFutureTask.cancel should remove from queue
     * */
    private volatile boolean removeOnCancel = false;


    public MyScheduledThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, MyRejectedExecutionHandler handler) {
        // todo 待优化
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /**
     * Returns current nanosecond time.
     */
    final long now() {
        return System.nanoTime();
    }

    private long triggerTime(long delay, TimeUnit unit) {
        return triggerTime(unit.toNanos((delay < 0) ? 0 : delay));
    }

    /**
     * Returns the trigger time of a delayed action.
     */
    long triggerTime(long delay) {
        return now() + ((delay < (Long.MAX_VALUE >> 1)) ? delay : overflowFree(delay));
    }

    /**
     * 调度任务对象
     * */
    private class MyScheduledFutureTask<V> extends FutureTask<V> implements RunnableScheduledFuture<V>{

        /**
         * 用于保证相同延迟时间的任务，其FIFO（先提交的先执行）的特性
         * */
        private final long sequenceNumber;

        /**
         * 当前任务下一次执行的时间(绝对时间，单位纳秒nanos)
         * */
        private long time;

        /**
         * 需要重复执行的任务使用的属性
         * 1 period>0,说明该任务是一个固定周期重复执行的任务（通过scheduleAtFixedRate方法提交）
         * 2 period<0，说明该任务是一个固定延迟重复执行的任务（通过scheduleWithFixedDelay方法提交）
         * 3 period=0，说明该任务是一个一次性执行的任务（通过schedule方法提交）
         * */
        private final long period;

        /**
         * todo 待研究
         * The actual task to be re-enqueued by reExecutePeriodic
         * */
        RunnableScheduledFuture<V> outerTask = this;

        /**
         * 基于二叉堆的延迟队列中的下标，用于快速的定位
         * Index into delay queue, to support faster cancellation.
         * */
        int heapIndex;

        /**
         * 一次性任务的构造函数（one action）
         * */
        MyScheduledFutureTask(Runnable runnable, V result, long ns) {
            super(runnable, result);
            // 下一次执行的时间
            this.time = ns;
            // 非周期性任务，period设置为0
            this.period = 0;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * 周期性任务的构造函数
         */
        MyScheduledFutureTask(Runnable runnable, V result, long ns, long period) {
            super(runnable, result);
            // 下一次执行的时间
            this.time = ns;
            this.period = period;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        @Override
        public boolean isPeriodic() {
            // period为0代表是一次性任务
            // period部位0代表是周期性任务
            return period != 0;
        }

        /**
         * 获得下一次执行的时间
         * */
        @Override
        public long getDelay(TimeUnit unit) {
            // 获得time属性与当前时间之差
            long delay = time - System.nanoTime();

            // 基于参数unit转换
            return unit.convert(delay, NANOSECONDS);
        }

        /**
         * 用于延迟队列中的优先级队列的大小比较
         * 基于time比较
         * 1. time越小，值越大（越早应该被调度执行的任务，越靠前）
         * 2. time相等就进一步比较sequenceNumber（调度时间一致的）
         * */
        @Override
        public int compareTo(Delayed other) {
            if (other == this) {
                // 同一个对象是相等的，返回0
                return 0;
            }

            if (other instanceof MyScheduledFutureTask) {
                // 同样是ScheduledFutureTask
                MyScheduledFutureTask<?> x = (MyScheduledFutureTask<?>)other;
                long diff = time - x.time;
                if (diff < 0) {
                    // 当前对象延迟时间更小，返回-1
                    return -1;
                } else if (diff > 0) {
                    // 当前对象延迟时间更大，返回1
                    return 1;
                } else if (sequenceNumber < x.sequenceNumber) {
                    // 延迟时间相等，比较序列号

                    // 当前对象序列号更小，需要排更前面返回-1
                    return -1;
                } else {
                    // 当前对象序列号更大，返回1
                    return 1;
                }
            }else{
                // 不是ScheduledFutureTask，通过getDelay比较延迟时间
                long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);

                // return (diff < 0) ? -1 : (diff > 0) ? 1 : 0
                if(diff < 0){
                    // 当前对象延迟时间小，返回-1
                    return -1;
                }else if(diff > 0){
                    // 当前对象延迟时间大，返回1
                    return 1;
                }else{
                    // 延迟时间相等返回0
                    return 0;
                }
            }
        }

        /**
         * 设置下一次执行的事件
         * */
        private void setNextRunTime() {
            long p = period;
            if (p > 0) {
                // fixedRate周期性任务，单纯的加period
                time += p;
            } else {
                //
                time = triggerTime(-p);
            }
        }

        /**
         * 取消
         * */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled && removeOnCancel && heapIndex >= 0) {
                // 取消成功 && removeOnCancel配置为true，且不是延迟队列（工作队列workerQueue）的第一个
                // 将任务对象从延迟工作队列中移除掉
                remove(this);
            }
            return cancelled;
        }

        @Override
        public void run() {
            // todo
//            boolean periodic = isPeriodic();
//            if (!canRunInCurrentRunState(periodic))
//                cancel(false);
//            else if (!periodic)
//                ScheduledThreadPoolExecutor.ScheduledFutureTask.super.run();
//            else if (ScheduledThreadPoolExecutor.ScheduledFutureTask.super.runAndReset()) {
//                setNextRunTime();
//                reExecutePeriodic(outerTask);
//            }
        }
    }


    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return null;
    }
}
