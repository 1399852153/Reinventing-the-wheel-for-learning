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

    /**
     * 获得下一次调度的绝对时间
     * */
    private long triggerTime(long delay, TimeUnit unit) {
        // 统一转成nanos级别计算
        return triggerTime(unit.toNanos((delay < 0) ? 0 : delay));
    }

    /**
     * 获得下一次调度的绝对时间
     * @param delay 延迟时间（单位nanos）
     */
    long triggerTime(long delay) {
        if(delay < (Long.MAX_VALUE >> 1)){
            // delay小于Long.MAX_VALUE/2，肯定不会发生compareTo时的溢出问题，直接正常累加delay即可
            return now() + delay;
        }else{
            // delay大于Long.MAX_VALUE/2，可能会发生compareTo时的溢出问题，在overflowFree检查并做必要的修正
            return now() + overflowFree(delay);
        }
    }

    /**
     * Constrains the values of all delays in the queue to be within
     * Long.MAX_VALUE of each other, to avoid overflow in compareTo.
     * This may occur if a task is eligible to be dequeued, but has
     * not yet been, while some other task is added with a delay of
     * Long.MAX_VALUE.
     *
     * 处理delay延迟时间溢出的问题
     * 举个例子：
     * 1. 当前队列里有一个理应出队被处理但实际还未处理的任务A（getDelay < 0），此时新提交了一个getDaley=Long.MAX_VALUE的任务B
     *    理论上来说，B是要等待很久很久才能执行的，入队后应该是放在A后面的，即A先于B执行（AB）
     * 2. 但如果不进行溢出处理的话，则入队过程中compareTo将会得到意料之外的结果
     *    假设A的getDelay为-10，B的getDelay为Long.MAX_VALUE，以延迟执行的时间作为依据A.compareTo(B)应该返回-1,代表A优先于B执行
     *    但实际上-10减去Long.MAX_VALUE会发生溢出，而得到一个正数，compareTo会返回1，导致B先于A执行（BA）
     * 3. 这样就发生了错乱，由于B的getDelay大的离谱，使得排在后面的A永远无法被执行了。所以必须通过overflowFree来限制一下
     */
    private long overflowFree(long delay) {
        // 获得当前队列中最早需要被调度的任务（peek方式获取）
        Delayed head = (Delayed) super.getQueue().peek();
        if (head != null) {
            long headDelay = head.getDelay(NANOSECONDS);
            // 当头结点延迟时间headDelay小于0，而参数delay非常大时（比如Long.MAX_VALUE）就会发生溢出（delay - headDelay < 0）
            // 正常的delay参数值减去一个负数是不可能小于0的
            if (headDelay < 0 && (delay - headDelay < 0)) {
                // 发生了溢出，则当前的delay值设置为Long.MAX_VALUE加上一个负值，保证当前的delay值的任务在入队时compareTo不会溢出，会被放在正确的位置上
                delay = Long.MAX_VALUE + headDelay;
            }
        }
        return delay;
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
