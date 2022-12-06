package threadpool.blog;

import threadpool.MyRejectedExecutionHandler;
import threadpool.MyScheduledExecutorService;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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

    /**
     * False if should cancel/suppress periodic tasks on shutdown.
     */
    private volatile boolean continueExistingPeriodicTasksAfterShutdown = false;

    /**
     * False if should cancel non-periodic tasks on shutdown.
     */
    private volatile boolean executeExistingDelayedTasksAfterShutdown = true;


    public MyScheduledThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, MyRejectedExecutionHandler handler) {
        // todo 待优化，构造方法里还差了一些逻辑
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
     * 尝试重新提交并执行周期性任务
     * */
    void reExecutePeriodic(RunnableScheduledFuture<?> task) {
        if (canRunInCurrentRunState(true)) {
            // 当前线程池状态允许执行任务，将任务加入到工作队列中去
            super.getQueue().add(task);
            // 再次检查，如果状态发生了变化，不允许了，则通过remove方法将刚加入的任务移除掉，实现回滚
            // 和ThreadPoolExecutor一致都是为了让shutdown/stop状态的线程池尽量在状态变更和提交新任务出现并发时，不要去执行新任务尽早终止线程池
            if (!canRunInCurrentRunState(true) && super.remove(task)) {
                task.cancel(false);
            } else {
                // 确保至少有一个工作线程会处理当前提交的任务
                super.ensurePrestart();
            }
        }
    }

    /**
     * Returns true if can run a task given current run state
     * and run-after-shutdown parameters.
     *
     * @param periodic true if this task periodic, false if delayed
     */
    boolean canRunInCurrentRunState(boolean periodic) {
        if(periodic){
            // 周期性任务，由continueExistingPeriodicTasksAfterShutdown决定
            return super.isRunningOrShutdown(continueExistingPeriodicTasksAfterShutdown);
        }else{
            // 非周期性任务（普通延迟任务），由executeExistingDelayedTasksAfterShutdown决定
            return super.isRunningOrShutdown(executeExistingDelayedTasksAfterShutdown);
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
         * 基于二叉堆的延迟队列中的数组下标，用于快速的查找、定位
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
                // （不用考虑溢出，因为如果因为time太大而溢出了（long类型溢出说明下一次执行时间是天荒地老），则永远不会被执行也是合理的）
                time += p;
            } else {
                // 下一次调度的时间（需要处理溢出）
                time = triggerTime(-p);
            }
        }

        /**
         * 取消当前任务的执行
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
            boolean periodic = isPeriodic();
            // 根据当前线程池状态，判断当前任务是否应该取消(比如已经是STOP了，就应该停止继续运行了)
            if (!canRunInCurrentRunState(periodic)) {
                // 不能正常运行，取消掉
                cancel(false);
            } else if (!periodic) {
                // 非周期性任务，当做普通的任务直接run就行了
                MyScheduledFutureTask.super.run();
            } else if (MyScheduledFutureTask.super.runAndReset()) {
                // 设置下一次执行的事件
                setNextRunTime();
                reExecutePeriodic(outerTask);
            }
        }
    }

    /**
     * 为调度任务线程池ScheduledThreadPoolExecutor专门定制的工作队列
     * 1.基于完全二叉堆结构，令执行时间最小（最近）的任务始终位于堆顶（即队列头） ==> 小顶堆
     * 2.实现上综合了juc包下的DelayQueue和PriorityQueue的功能，并加上了一些基于ScheduledThreadPoolExecutor的一些逻辑
     *   建议读者在理解了PriorityQueue、DelayQueue原理之后再来学习其工作机制，循序渐进而事半功倍
     * */
    static class DelayedWorkQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {
        /**
         * 完全二叉堆底层数组的初始容量
         * */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * 完全二叉堆底层数组
         * */
        private RunnableScheduledFuture<?>[] queue = new RunnableScheduledFuture<?>[INITIAL_CAPACITY];

        /**
         * 互斥锁，用于入队等操作时的并发控制
         * */
        private final ReentrantLock lock = new ReentrantLock();

        /**
         * 队列中任务元素的数量
         * */
        private int size = 0;

        /**
         * 等待执行队列头（最早应该执行的）任务的线程池工作线程
         * 为什么会引入一个这个呢？是为了减少其它线程take获取新任务时不必要的等待
         * 因为额外引入了一个操作系统层面的定时器，await带超时时间比无限时间的await性能要差一些
         * */
        private Thread leader = null;

        /**
         * 当一个队列头部的任务可以被执行时，通知等待在available上的工作线程
         * */
        private final Condition available = lock.newCondition();

        // ============================= 内部private的辅助方法 ================================
        private void setIndex(RunnableScheduledFuture<?> f, int index) {
            if (f instanceof MyScheduledFutureTask) {
                // 如果任务对象是MyScheduledFutureTask类型，而不仅仅是RunnableScheduledFuture
                // 则设置index属性便于加速查找
                ((MyScheduledFutureTask<?>) f).heapIndex = index;
            }
        }

        /**
         * 第k位的元素在二叉堆中上滤（小顶堆：最小的元素在堆顶）
         *
         * 当新元素插入完全二叉堆时，我们直接将其插入向量末尾(堆底最右侧)，此时新元素的优先级可能会大于其双亲元素甚至祖先元素，破坏了堆序性，
         * 因此我们需要对插入的新元素进行一次上滤操作，使完全二叉堆恢复堆序性。
         * 由于堆序性只和双亲和孩子节点相关，因此堆中新插入元素的非祖先元素的堆序性不会受到影响，上滤只是一个局部性的行为。
         * */
        private void siftUp(int k, RunnableScheduledFuture<?> key) {
            while (k > 0) {
                // 获得第k个节点逻辑上的双亲节点
                //     0
                //   1   2
                //  3 4 5 6
                // （下标减1再除2，比如下标为5和6的元素逻辑上的parent就是下标为2的元素）
                int parent = (k - 1) >>> 1;

                // 拿到双亲节点对应的元素
                RunnableScheduledFuture<?> e = queue[parent];
                if (key.compareTo(e) >= 0) {
                    // 如果当前需要上滤的元素key，其值大于或等于双亲节点就停止上滤过程（小顶堆）
                    break;
                }

                // 当前上滤的元素key，其值小于双亲节点
                // 将双亲节点换下来，到第k位上（把自己之前的位置空出来）
                queue[k] = e;
                // 设置被换下来的双亲节点的index值
                setIndex(e, k);

                // 令k下标变为更小的parent，继续尝试下一轮上滤操作
                k = parent;
            }

            // 上滤判断结束后，最后空出来的parent的下标值对应的位置由存放上滤的元素key
            queue[k] = key;
            // 设置key节点的index值
            setIndex(key, k);
        }

        /**
         * 第k位的元素在二叉堆中下滤（小顶堆：最小的元素在堆顶）
         *
         * 当优先级队列中极值元素出队时，需要在满足堆序性的前提下，选出新的极值元素。
         * 我们简单的将当前向量末尾的元素放在堆顶，堆序性很有可能被破坏了。此时，我们需要对当前的堆顶元素进行一次下滤操作，使得整个完全二叉堆恢复堆序性。
         * */
        private void siftDown(int k, RunnableScheduledFuture<?> key) {
            // half为size的一半
            int half = size >>> 1;
            // k小于half才需要下滤，大于half说明第k位元素已经是叶子节点了，不需要继续下滤了
            while (k < half) {
                // 获得第k位元素逻辑上的左孩子节点的下标
                int child = (k << 1) + 1;
                // 获得左孩子的元素
                RunnableScheduledFuture<?> c = queue[child];
                // 获得第k位元素逻辑上的右孩子节点的下标
                int right = child + 1;

                // right没有越界，则比较左右孩子值的大小
                if (right < size && c.compareTo(queue[right]) > 0) {
                    // 左孩子大于右孩子，所以用右孩子和key比较，c=右孩子节点
                    // （if条件不满足，则用左孩子和key比较，c=左孩子节点）
                    c = queue[child = right];
                }

                // key和c比较，如果key比左右孩子都小，则结束下滤
                if (key.compareTo(c) <= 0) {
                    break;
                }

                // key大于左右孩子中更小的那个，则第k位换成更小的那个孩子（保证上层的节点永远小于其左右孩子，保证堆序性）
                queue[k] = c;
                // 设置被换到上层去的孩子节点的index的值
                setIndex(c, k);
                // 令下标k变大为child，在循环中尝试更下一层的下滤操作
                k = child;
            }

            // 结束了下滤操作，最后将元素key放到最后被空出来的孩子节点原来的位置
            queue[k] = key;
            // 设置key的index值
            setIndex(key, k);
        }

        // ============================= 实现接口定义的方法 ======================================
        @Override
        public Iterator<Runnable> iterator() {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void put(Runnable runnable) throws InterruptedException {

        }

        @Override
        public boolean offer(Runnable runnable, long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public Runnable take() throws InterruptedException {
            return null;
        }

        @Override
        public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            return null;
        }

        @Override
        public int remainingCapacity() {
            return 0;
        }

        @Override
        public int drainTo(Collection<? super Runnable> c) {
            return 0;
        }

        @Override
        public int drainTo(Collection<? super Runnable> c, int maxElements) {
            return 0;
        }

        @Override
        public boolean offer(Runnable runnable) {
            return false;
        }

        @Override
        public Runnable poll() {
            return null;
        }

        @Override
        public Runnable peek() {
            return null;
        }
    }
}
