package threadpool.blog;

import threadpool.MyRejectedExecutionHandler;
import threadpool.MyScheduledExecutorService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * MyScheduledThreadPoolExecutor
 * */
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

    public MyScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory, MyRejectedExecutionHandler handler) {
        // 工作队列DelayedWorkQueue是无界队列，只需要指定corePoolSize即可，maximumPoolSize没用
        // keepAliveTime=0，一般来说核心线程是不应该退出的，除非父类里allowCoreThreadTimeOut被设置为true了
        // 那样没有任务时核心线程就会立即被回收了(keepAliveTime=0, allowCoreThreadTimeOut=true)
        super(corePoolSize, Integer.MAX_VALUE, 0, NANOSECONDS, new MyDelayedWorkQueue(), threadFactory, handler);
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
     * Main execution method for delayed or periodic tasks.  If pool
     * is shut down, rejects the task. Otherwise adds task to queue
     * and starts a thread, if necessary, to run it.  (We cannot
     * prestart the thread to run the task because the task (probably)
     * shouldn't be run yet.)  If the pool is shut down while the task
     * is being added, cancel and remove it if required by state and
     * run-after-shutdown parameters.
     *
     * @param task the task
     */
    private void delayedExecute(RunnableScheduledFuture<?> task) {
        if (isShutdown()) {
            // 线程池已经终止了，执行reject拒绝策略
            super.reject(task);
        } else {
            // 没有终止，任务在工作队列中入队
            super.getQueue().add(task);
            // 再次检查状态，如果线程池已经终止则回滚（将任务对象从工作队列中remove掉，并且当前任务Future执行cancel方法取消掉）
            // 在提交任务与线程池终止并发时，推进线程池尽早到达终结态
            if (isShutdown() && !canRunInCurrentRunState(task.isPeriodic()) && remove(task)) {
                task.cancel(false);
            } else {
                // 确保至少有一个工作线程会处理当前提交的任务
                ensurePrestart();
            }
        }
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
        if (command == null || unit == null) {
            throw new NullPointerException();
        }

        // 装饰任务对象
        RunnableScheduledFuture<?> t = decorateTask(command,
                new MyScheduledFutureTask<Void>(command, null, triggerTime(delay, unit)));

        // 提交任务到工作队列中，以令工作线程满足条件时将其取出来调度执行
        delayedExecute(t);

        return t;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (command == null || unit == null) {
            throw new NullPointerException();
        }
        if (period <= 0) {
            throw new IllegalArgumentException();
        }

        // 固定周期重复执行的任务，period参数为正数
        MyScheduledFutureTask<Void> scheduledFutureTask = new MyScheduledFutureTask<>(
                command, null, triggerTime(initialDelay, unit), unit.toNanos(period));

        // 装饰任务对象
        RunnableScheduledFuture<Void> t = decorateTask(command, scheduledFutureTask);

        // 记录用户实际提交的任务对象
        scheduledFutureTask.outerTask = t;

        // 提交任务到工作队列中，以令工作线程满足条件时将其取出来调度执行
        delayedExecute(t);

        return t;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        if (delay <= 0)
            throw new IllegalArgumentException();

        // 固定延迟重复执行的任务,period参数为负数
        MyScheduledFutureTask<Void> scheduledFutureTask =
                new MyScheduledFutureTask<>(command, null, triggerTime(initialDelay, unit), unit.toNanos(-delay));

        // 装饰任务对象
        RunnableScheduledFuture<Void> t = decorateTask(command, scheduledFutureTask);

        // 记录用户实际提交的任务对象
        scheduledFutureTask.outerTask = t;

        // 提交任务到工作队列中，以令工作线程满足条件时将其取出来调度执行
        delayedExecute(t);

        return t;
    }

    /**
     * 可以由用户子类拓展的，可以装饰原始RunnableScheduledFuture任务对象
     * */
    protected <V> RunnableScheduledFuture<V> decorateTask(
            Runnable runnable, RunnableScheduledFuture<V> task) {
        return task;
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
    static class MyDelayedWorkQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {
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
         * Call only when holding lock.
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
         * Call only when holding lock.
         *
         * 当优先级队列中极值元素出队时，需要在满足堆序性的前提下，选出新的极值元素。
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

        /**
         * 二叉堆扩容
         * Call only when holding lock.
         */
        private void grow() {
            int oldCapacity = queue.length;
            // 在原有基础上扩容50%
            int newCapacity = oldCapacity + (oldCapacity >> 1); // grow 50%
            if (newCapacity < 0) {
                // 处理扩容50%后整型溢出
                newCapacity = Integer.MAX_VALUE;
            }
            // 将原来数组中的数组数据复制到新数据
            // 令成员变量queue指向扩容后的新数组
            queue = Arrays.copyOf(queue, newCapacity);
        }

        /**
         * 查询x在二叉堆中的数组下标
         * @return 找到了就返回具体的下标，没找到返回-1
         * */
        private int indexOf(Object x) {
            if(x == null){
                // 为空，直接返回-1
                return -1;
            }

            if (x instanceof MyScheduledFutureTask) {
                int i = ((MyScheduledFutureTask) x).heapIndex;
                // 为什么不直接以x.heapIndex为准?
                // 因为可能对象x来自其它的线程池，而不是本线程池的
                // (先判断i是否合法，然后判断第heapIndex个是否就是x)
                if (i >= 0 && i < size && queue[i] == x) {
                    // 比对一下第heapIndex项是否就是x，如果是则直接返回
                    return i;
                }else{
                    // heapIndex不合法或者queue[i] != x不相等，说明不是本线程池的任务对象，返回-1
                    return -1;
                }
            } else {
                // 非ScheduledFutureTask，从头遍历到尾进行线性的检查
                for (int i = 0; i < size; i++) {
                    // 如果x和第i个相等，则返回i
                    if (x.equals(queue[i])) {
                        return i;
                    }
                }

                // 遍历完了整个queue都没找到，返回-1
                return -1;
            }
        }

        // ============================= 实现接口定义的方法 ======================================
        @Override
        public boolean contains(Object x) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                // 不为-1就是存在，返回true
                // 反之就是不存在，返回false
                return indexOf(x) != -1;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean remove(Object x) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                int i = indexOf(x);
                if (i < 0) {
                    // x不存在，直接返回
                    return false;
                }

                // x存在，先将其index设置为-1
                setIndex(queue[i], -1);
                // 二叉堆元素数量自减1
                int s = --size;

                // 将队列最尾端的元素取出
                RunnableScheduledFuture<?> replacement = queue[s];
                queue[s] = null;

                // s == i,说明被删除的是最尾端的元素，移除后没有破坏堆序性，直接返回即可
                if (s != i) {
                    // 将队列最尾端的元素放到被移除元素的位置，进行一次下滤
                    siftDown(i, replacement);
                    if (queue[i] == replacement) {
                        // 这里为什么还要上滤一次呢？其实是最尾端的元素replacement在放到第i个位置上执行下滤后，其虽然保证了小于其左右孩子节点，但依然可能大于其双亲节点
                        // 举个例子：
                        //                0
                        //         10           1
                        //      20   30      2    3
                        //    40 50 60 70   4 5  6 7
                        // 如果删除第3排第一个的20，则siftDown后会变成:
                        //                0
                        //         10           1
                        //      7    30      2    3
                        //    40 50 60 70   4 5  6
                        // replacement=7是小于其双亲节点10的，因此需要再进行一次上滤，使得最终结果为：
                        //                0
                        //         7           1
                        //      10   30      2    3
                        //    40 50 60 70   4 5  6
                        // 这种需要上滤的情况是相对特殊的，只有当下滤只有这个节点没有动（即下滤后queue[i] == replacement）
                        // 因为这种情况下replacement不进行上滤的话**可能**小于其双亲节点，而违反了堆序性（heap invariant）
                        // 而如果下滤后移动了位置（queue[i] != replacement），则其必定大于其双亲节点,因此不需要尝试上滤了
                        siftUp(i, replacement);

                        // 额外的：
                        // 最容易理解的实现删除堆中元素的方法是将replacement置于堆顶（即第0个位置），进行一次时间复杂度为O（log n）的完整下滤而恢复堆序性
                        // 但与ScheduledThreadExecutor在第i个位置上进行下滤操作的算法相比其时间复杂度是更高的
                        // jdk的实现中即使下滤完成后再进行一次上滤，其**最差情况**也与从堆顶开始下滤的算法的性能一样。虽然难理解一些但却是更高效的堆元素删除算法
                        // 在remove方法等移除队列中间元素时，会比从堆顶直接下滤效率高
                    }
                }
                return true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int size() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public int remainingCapacity() {
            // 无界队列，按照接口约定返回Integer.MAX_VALUE
            return Integer.MAX_VALUE;
        }

        @Override
        public RunnableScheduledFuture<?> peek() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                // 返回队列头元素
                return queue[0];
            } finally {
                lock.unlock();
            }
        }

        /**
         * 入队操作
         * */
        @Override
        public boolean offer(Runnable x) {
            if (x == null) {
                throw new NullPointerException();
            }

            RunnableScheduledFuture<?> e = (RunnableScheduledFuture<?>)x;
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                int i = size;
                if (i >= queue.length) {
                    // 容量不足，扩容
                    grow();
                }
                size = i + 1;
                if (i == 0) {
                    // 队列此前为空，第0位设置就ok了
                    queue[0] = e;
                    setIndex(e, 0);
                } else {
                    // 队列此前不为空，加入队尾进行一次上滤，恢复堆序性
                    siftUp(i, e);
                }

                // 插入堆后，发现自己是队列头（最早要执行的任务）
                if (queue[0] == e) {
                    // 已经有新的队列头任务了，leader设置为空
                    leader = null;
                    // 通知take时阻塞等待获取新任务的工作线程
                    available.signal();
                }
            } finally {
                lock.unlock();
            }

            // 无界队列，入队一定成功
            return true;
        }

        @Override
        public void put(Runnable runnable){
            offer(runnable);
        }

        @Override
        public boolean add(Runnable e) {
            return offer(e);
        }

        @Override
        public boolean offer(Runnable e, long timeout, TimeUnit unit) {
            // 无界队列，offer无需等待
            return offer(e);
        }

        /**
         * 队列元素f出队操作（修改size等数据，并且恢复f移除后的堆序性）
         * */
        private RunnableScheduledFuture<?> finishPoll(RunnableScheduledFuture<?> f) {
            // size自减1
            int s = --size;
            RunnableScheduledFuture<?> x = queue[s];
            queue[s] = null;
            if (s != 0) {
                // 由于队列头元素出队了，把队尾元素放到队头进行一次下滤，以恢复堆序性
                siftDown(0, x);
            }
            // 被移除的元素f，index设置为-1
            setIndex(f, -1);

            // 返回被移除队列的元素
            return f;
        }

        /**
         * 出队操作（非阻塞）
         * */
        @Override
        public RunnableScheduledFuture<?> poll() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                RunnableScheduledFuture<?> first = queue[0];
                if (first == null || first.getDelay(NANOSECONDS) > 0) {
                    // 如果队列为空或者队头元素没到需要执行的时间点（delay>0），返回null
                    return null;
                } else {
                    // 返回队列头元素，并且恢复堆序性
                    return finishPoll(first);
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public RunnableScheduledFuture<?> take() throws InterruptedException {
            final ReentrantLock lock = this.lock;
            // take是可响应中断的
            lock.lockInterruptibly();
            try {
                for (;;) {
                    RunnableScheduledFuture<?> first = queue[0];
                    if (first == null) {
                        // 队列为空，await等待（可响应中断）
                        available.await();
                    } else {
                        // 队列不为空
                        long delay = first.getDelay(NANOSECONDS);
                        if (delay <= 0) {
                            // 队列头的元素delay<=0，到可执行的时间点了，返回即可
                            return finishPoll(first);
                        }
                        // first设置为null，便于await期间提早gc这个临时变量
                        first = null; // don't retain ref while waiting

                        if (leader != null){
                            // leader不为空，说明已经有别的线程在take等待了，await无限等待
                            // （不带超时时间的await性能更好一些，队列头元素只需要由leader线程来获取就行，其它的线程就等leader处理完队头任务后将其唤醒）
                            available.await();
                        } else {
                            // leader为空，说明之前没有别的线程在take等待
                            Thread thisThread = Thread.currentThread();
                            // 令当前线程为leader
                            leader = thisThread;
                            try {
                                // leader await带超时时间（等待队列头的任务delay时间，确保任务可执行时第一时间被唤醒去执行）
                                available.awaitNanos(delay);
                            } finally {
                                if (leader == thisThread) {
                                    // take方法退出，当前线程不再是leader了
                                    leader = null;
                                }
                            }
                        }
                    }
                }
            } finally {
                if (leader == null && queue[0] != null) {
                    // leader为空，且队列不为空（比如leader线程被唤醒后，通过finishPoll已经获得了之前的队列头元素）
                    // 尝试唤醒之前阻塞等待的那些消费者线程
                    available.signal();
                }
                lock.unlock();
            }
        }

        @Override
        public RunnableScheduledFuture<?> poll(long timeout, TimeUnit unit) throws InterruptedException {
            long nanos = unit.toNanos(timeout);
            final ReentrantLock lock = this.lock;
            // pool是可响应中断的
            lock.lockInterruptibly();
            try {
                for (;;) {
                    RunnableScheduledFuture<?> first = queue[0];
                    if (first == null) {
                        // 队列为空
                        if (nanos <= 0) {
                            // timeout等待时间超时了，返回null(一般不是第一次循环)
                            return null;
                        } else {
                            // 队列元素为空，等待timeout
                            nanos = available.awaitNanos(nanos);
                        }
                    } else {
                        // 队列不为空
                        long delay = first.getDelay(NANOSECONDS);
                        if (delay <= 0) {
                            // delay<=0,队头元素满足出队条件
                            return finishPoll(first);
                        }
                        if (nanos <= 0) {
                            // 队列不为空，但是timeout等待时间超时了，返回null(一般不是第一次循环)
                            return null;
                        }
                        // first设置为null，便于await期间提早gc这个临时变量
                        first = null; // don't retain ref while waiting
                        if (nanos < delay || leader != null) {
                            // poll指定的等待时间小于队头元素delay的时间，或者leader不为空(之前已经有别的线程在等待了捞取任务了)
                            // 最多等待到timeout
                            nanos = available.awaitNanos(nanos);
                        } else {
                            // 队头元素delay的时间早于waitTime指定的时间，且此前leader为null
                            // 当前线程成为leader
                            Thread thisThread = Thread.currentThread();
                            leader = thisThread;
                            try {
                                // 等待delay时间
                                long timeLeft = available.awaitNanos(delay);
                                // 醒来后，nanos自减
                                nanos -= delay - timeLeft;
                            } finally {
                                if (leader == thisThread) {
                                    leader = null;
                                }
                            }
                        }
                    }
                }
            } finally {
                if (leader == null && queue[0] != null) {
                    // leader为空，且队列不为空（比如leader线程被唤醒后，通过finishPoll已经获得了之前的队列头元素）
                    // 尝试唤醒之前阻塞等待的那些消费者线程
                    available.signal();
                }
                lock.unlock();
            }
        }

        @Override
        public void clear() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                for (int i = 0; i < size; i++) {
                    RunnableScheduledFuture<?> t = queue[i];
                    if (t != null) {
                        // 将队列内部数组的值全部设置为null
                        queue[i] = null;
                        // 所有任务对象的index都设置为-1
                        setIndex(t, -1);
                    }
                }
                size = 0;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns first element only if it is expired.
         * Used only by drainTo.  Call only when holding lock.
         */
        private RunnableScheduledFuture<?> peekExpired() {
            RunnableScheduledFuture<?> first = queue[0];

            // 如果队头元素存在，且已到期（expired） 即delay <= 0,返回队头元素，否则返回null
            return (first == null || first.getDelay(NANOSECONDS) > 0) ? null : first;
        }

        @Override
        public int drainTo(Collection<? super Runnable> c) {
            if (c == null) {
                throw new NullPointerException();
            }
            if (c == this) {
                throw new IllegalArgumentException();
            }
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                RunnableScheduledFuture<?> first;
                int n = 0;
                // 延迟队列的drainTo只返回已过期的所有元素
                while ((first = peekExpired()) != null) {
                    // 已过期的元素加入参数指定的集合
                    c.add(first);   // In this order, in case add() throws.
                    // 同时将其从队列中移除
                    finishPoll(first);
                    // 总共迁移元素的个数自增
                    ++n;
                }

                // 队列为空，或者队列头元素未过期则跳出循环
                // 返回总共迁移元素的个数
                return n;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int drainTo(Collection<? super Runnable> c, int maxElements) {
            if (c == null) {
                throw new NullPointerException();
            }
            if (c == this) {
                throw new IllegalArgumentException();
            }
            if (maxElements <= 0) {
                return 0;
            }

            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                RunnableScheduledFuture<?> first;
                int n = 0;
                // 延迟队列的drainTo只返回已过期的所有元素,且当前总共迁移元素的个数不超过参数maxElements的限制
                while (n < maxElements && (first = peekExpired()) != null) {
                    // 已过期的元素加入参数指定的集合
                    c.add(first);   // In this order, in case add() throws.
                    // 同时将其从队列中移除
                    finishPoll(first);
                    // 实际总迁移元素的个数自增
                    ++n;
                }

                // 队列为空，或者队列头元素未过期则跳出循环
                // 返回实际总迁移元素的个数
                return n;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Object[] toArray() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                // 队列底层本来就是数组，直接copy一份即可
                return Arrays.copyOf(queue, size, Object[].class);
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T[] toArray(T[] a) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                // 队列底层本来就是数组，直接copy一份即可
                if (a.length < size) {
                    return (T[]) Arrays.copyOf(queue, size, a.getClass());
                }
                System.arraycopy(queue, 0, a, 0, size);
                if (a.length > size) {
                    a[size] = null;
                }
                return a;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Iterator<Runnable> iterator() {
            return new MyDelayedWorkQueue.Itr(Arrays.copyOf(queue, size));
        }

        /**
         * Snapshot iterator that works off copy of underlying q array.
         *
         * 迭代器为当前队列底层数组的迭代器
         */
        private class Itr implements Iterator<Runnable> {
            final RunnableScheduledFuture<?>[] array;
            int cursor = 0;     // index of next element to return
            int lastRet = -1;   // index of last element, or -1 if no such

            Itr(RunnableScheduledFuture<?>[] array) {
                this.array = array;
            }

            public boolean hasNext() {
                return cursor < array.length;
            }

            public Runnable next() {
                if (cursor >= array.length) {
                    throw new NoSuchElementException();
                }
                lastRet = cursor;
                return array[cursor++];
            }

            public void remove() {
                if (lastRet < 0) {
                    throw new IllegalStateException();
                }
                MyDelayedWorkQueue.this.remove(array[lastRet]);
                lastRet = -1;
            }
        }
    }
}
