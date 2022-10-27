package threadpool.v2;

import threadpool.MyRejectedExecutionHandler;
import threadpool.MyThreadPoolExecutor;
import threadpool.v1.MyThreadPoolExecutorV1;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xiongyx
 * @date 2021/5/7
 */
public class MyThreadPoolExecutorV2 implements MyThreadPoolExecutor {

    /**
     * 指定的核心线程数量
     */
    private volatile int corePoolSize;

    /**
     * 指定的最大线程数量
     * */
    private volatile int maximumPoolSize;

    /**
     * 线程保活时间(单位：纳秒 nanos)
     * */
    private volatile long keepAliveTime;

    /**
     * 存放任务的工作队列(阻塞队列)
     * */
    private volatile BlockingQueue<Runnable> workQueue;

    /**
     * 线程工厂
     * */
    private volatile ThreadFactory threadFactory;

    /**
     * 拒绝策略
     * */
    private volatile MyRejectedExecutionHandler handler;

    /**
     * 维护当前存活的worker线程集合
     * */
    private final HashSet<MyWorker> workers = new HashSet<>();

    /**
     * 主控锁
     * */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * 用于awaitTermination逻辑的条件变量
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * 跟踪线程池曾经有过的最大线程数量（只能在mainLock的并发保护下更新）
     */
    private int largestPoolSize;

    /**
     * 当前线程池已完成的任务数量
     * */
    private long completedTaskCount;

    /**
     * 是否允许核心线程在idle一定时间后被销毁（和非核心线程一样）
     * */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * 用于shutdown/shutdownNow时，检查当前调用者是否有权限去通过interrupt方法去中断对应工作线程
     * */
    private static final RuntimePermission shutdownPerm = new RuntimePermission("modifyThread");

    /**
     * 默认的拒绝策略
     */
    private static final MyRejectedExecutionHandler defaultHandler = new MyThreadPoolExecutorV1.MyAbortPolicy();

    /**
     * 当前线程池中存在的worker线程数量 + 状态的一个聚合（通过一个原子int进行cas，来避免对两个业务属性字段加锁来保证一致性）
     * v1版本只关心
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;

    /**
     * 32位的有符号整数，有3位是用来存放线程池状态的，所以用来维护当前工作线程个数的部分就只能用29位了
     * 被占去的3位中，有1位原来的符号位，2位是原来的数值位。所以的值为Integer.MAX的4分之一((Integer.MAX_VALUE / 4) == CAPACITY)
     * */
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    /**
     * 线程池状态poolStatus常量（状态值只会由小到大，单调递增）
     * 线程池状态迁移图：
     *         ↗ SHUTDOWN ↘
     * RUNNING       ↓       TIDYING → TERMINATED
     *         ↘   STOP   ↗
     * 1 RUNNING状态，代表着线程池处于正常运行的状态。能正常的接收并处理提交的任务
     * 线程池对象初始化时，状态为RUNNING
     * 对应逻辑：private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
     *
     * 2 SHUTDOWN状态，代表线程池处于停止对外服务的状态。不再接收新提交的任务，但依然会将workQueue工作队列中积压的任务处理完
     * 调用了shutdown方法时，状态由RUNNING -> SHUTDOWN
     * 对应逻辑：shutdown方法中的advanceRunState(SHUTDOWN);
     *
     * 3 STOP状态，代表线程池处于停止状态。不再接受新提交的任务，同时也不再处理workQueue工作队列中积压的任务，当前还在处理任务的工作线程将收到interrupt中断通知
     * 之前未调用shutdown方法，直接调用了shutdownNow方法，状态由RUNNING -> STOP
     * 之前先调用了shutdown方法，后调用了shutdownNow方法，状态由SHUTDOWN -> STOP
     * 对应逻辑：shutdownNow方法中的advanceRunState(STOP);
     *
     * 4 TIDYING状态，代表着线程池即将完全终止，正在做最后的收尾工作
     * 当前线程池状态为SHUTDOWN,任务被消费完工作队列workQueue为空，且工作线程全部退出完成工作线程集合workers为空时，tryTerminate方法中将状态由SHUTDOWN->TIDYING
     * 当前线程池状态为STOP,工作线程全部退出完成工作线程集合workers为空时，tryTerminate方法中将状态由STOP->TIDYING
     * 对应逻辑：tryTerminate方法中的ctl.compareAndSet(c, ctlOf(TIDYING, 0)
     *
     * 5 TERMINATED状态，代表着线程池完全的关闭。之前线程池已经处于TIDYING状态，且调用的钩子函数terminated已返回
     * 当前线程池状态为TIDYING，调用的钩子函数terminated已返回
     * 对应逻辑：tryTerminate方法中的ctl.set(ctlOf(TERMINATED, 0));
     * */
    private static final int RUNNING = -1 << COUNT_BITS;
    private static final int SHUTDOWN = 0 << COUNT_BITS;
    private static final int STOP = 1 << COUNT_BITS;
    private static final int TIDYING = 2 << COUNT_BITS;
    private static final int TERMINATED = 3 << COUNT_BITS;

    // Packing and unpacking ctl
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    /*
     * Bit field accessors that don't require unpacking ctl.
     * These depend on the bit layout and on workerCount being never negative.
     */
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    private boolean compareAndIncrementWorkerCount(int expect) {
        return this.ctl.compareAndSet(expect, expect + 1);
    }
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    private void decrementWorkerCount() {
        do {
            // cas更新，workerCount自减1
        } while (!compareAndDecrementWorkerCount(ctl.get()));
    }

    public MyThreadPoolExecutorV2(int corePoolSize,
                                  int maximumPoolSize,
                                  long keepAliveTime,
                                  TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue,
                                  ThreadFactory threadFactory,
                                  MyRejectedExecutionHandler handler) {
        // 基本的参数校验
        if (corePoolSize < 0 || maximumPoolSize <= 0 || maximumPoolSize < corePoolSize || keepAliveTime < 0) {
            throw new IllegalArgumentException();
        }

        if (unit == null || workQueue == null || threadFactory == null || handler == null) {
            throw new NullPointerException();
        }

        // 设置成员变量
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    @Override
    public void execute(Runnable command) {
        if (command == null){
            throw new NullPointerException("command参数不能为空");
        }

        int currentCtl = this.ctl.get();
        if (workerCountOf(currentCtl) < corePoolSize) {
            // 如果当前存在的worker线程数量低于指定的核心线程数量，则创建新的核心线程
            boolean addCoreWorkerSuccess = addWorker(command,true);
            if(addCoreWorkerSuccess){
                // addWorker添加成功，直接返回即可
                return;
            }

            // addWorker失败了
            // 失败的原因主要有以下几个：
            // 1 线程池的状态出现了变化，比如调用了shutdown/shutdownNow方法，不再是RUNNING状态，停止接受新的任务
            // 2 多个线程并发的execute提交任务，导致cas失败，重试后发现当前线程的个数已经超过了限制
            // 3 小概率是ThreadFactory线程工厂没有正确的返回一个Thread

            // 获取最新的ctl状态
            currentCtl = this.ctl.get();
        }

        // 走到这里有两种情况
        // 1 因为核心线程超过限制（workerCountOf(currentCtl) < corePoolSize == false），需要尝试尝试将任务放入阻塞队列
        // 2 addWorker返回false，创建核心工作线程失败

        // 判断当前线程池状态是否为running
        // 如果是running状态，则进一步执行任务入队操作
        if(isRunning(currentCtl) && this.workQueue.offer(command)){
            // 线程池是running状态，且workQueue.offer入队成功

            int recheck = this.ctl.get();
            // 重新检查状态，避免在上面入队的过程中线程池并发的关闭了
            // 如果是isRunning=false，则进一步需要通过remove操作将刚才入队的任务删除，进行回滚
            if (!isRunning(recheck) && remove(command)) {
                // 线程池关闭了，执行reject操作
                reject(command);
            } else if(workerCountOf(currentCtl) == 0){
                // 在corePoolSize为0的情况下，当前不存在存活的核心线程
                // 一个任务在入队之后，如果当前线程池中一个线程都没有，则需要兜底的创建一个非核心线程来处理入队的任务
                // 因此firstTask为null，目的是先让任务先入队后创建线程去拉取任务并执行
                addWorker(null,false);
            }else{
                // 加入队列成功，且当前存在worker线程，成功返回
                return;
            }
        }else{
            // 阻塞队列已满，尝试创建一个新的非核心线程处理
            boolean addNonCoreWorkerSuccess = addWorker(command,false);
            if(!addNonCoreWorkerSuccess){
                // 创建非核心线程失败，执行拒绝策略（失败的原因和前面创建核心线程addWorker的原因类似）
                reject(command);
            }else{
                // 创建非核心线程成功，成功返回
                return;
            }
        }
    }

    /**
     * jdk里面submit方法是放在父类AbstractExecutorService里的
     * */
    public Future<?> submit(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }
        // 把Runnable包装成FutureTask（同时实现了两个接口，既是Runnable又是Future）
        RunnableFuture<Void> ftask = new FutureTask<>((Callable<Void>) task);
        execute(ftask);
        return ftask;
    }

    @Override
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        // 当一个任务从工作队列中被成功移除，可能此时工作队列为空。尝试判断是否满足线程池中止条件
        tryTerminate();
        return removed;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0) {
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        // 判断一下新旧值是否相等，避免无意义的volatile变量更新，导致不必要的cpu cache同步
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value) {
                // 参数值value为true，说明之前不允许核心线程由于idle超时而退出
                // 而此时更新为true说明现在允许了，则通过interruptIdleWorkers唤醒所有的idle线程
                // 令其走一遍runWorker中的逻辑，尝试着让idle超时的核心线程及时销毁
                interruptIdleWorkers();
            }
        }
    }

    /**
     * 动态更新核心线程最大值corePoolSize
     * */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0) {
            throw new IllegalArgumentException();
        }

        // 计算差异
        int delta = corePoolSize - this.corePoolSize;
        // 赋值
        this.corePoolSize = corePoolSize;
        if (workerCountOf(this.ctl.get()) > corePoolSize) {
            // 更新完毕后，发现当前工作线程数超过了指定的值
            // 唤醒所有idle线程，让目前空闲的idle超时的线程及时销毁
            interruptIdleWorkers();
        } else if (delta > 0) {
            // 差异大于0，代表着新值大于旧值

            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            // 我们无法确切的知道有多少新的线程是所需要的。
            // 以启发式的，预先启动足够的新工作线程，用于处理工作队列中的任务
            // 但当执行此操作时工作队列为空了，则立即停止此操作

            // 取差异和当前工作队列中的最小值为k
            int k = Math.min(delta, workQueue.size());

            // 尝试着一直增加新的工作线程，直到和k相同
            // 这样设计的目的在于控制增加的核心线程数量，不要一下子创建过多核心线程
            // 举个例子：原来的corePoolSize是10，且工作线程数也是10，现在新值设置为了30，新值比旧值大20，理论上应该直接创建20个核心工作线程
            // 而工作队列中的任务数只有10，那么这个时候直接创建20个新工作线程是没必要的，只需要一个一个创建，在创建的过程中新的线程会尽量的消费工作队列中的任务
            // 这样就可以以一种启发性的方式创建合适的新工作线程，一定程度上节约资源。后面再有新的任务提交时，再从runWorker方法中去单独创建核心线程(类似惰性创建)
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty()) {
                    // 其它工作线程在循环的过程中也在消费工作线程，且用户也可能不断地提交任务
                    // 这是一个动态的过程，但一旦发现当前工作队列为空则立即结束
                    break;
                }
            }
        }
    }

    /**
     * 动态更新最大线程数maximumPoolSize
     * */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize) {
            throw new IllegalArgumentException();
        }
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(this.ctl.get())  > maximumPoolSize) {
            // 更新完毕后，发现当前工作线程数超过了指定的值
            // 唤醒所有idle线程，让目前空闲的idle超时的线程及时销毁
            interruptIdleWorkers();
        }
    }

    @Override
    public BlockingQueue<Runnable> getQueue() {
        return this.workQueue;
    }

    /**
     * 获得当前线程池的工作线程个数
     * */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 如果已经是TIDYING或者TERMINATED了，返回0
            // 否则返回工作线程集合workers的size
            return runStateAtLeast(ctl.get(), TIDYING) ? 0 : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 关闭线程池（不再接收新任务，但已提交的任务会全部被执行）
     * 但不会等待任务彻底的执行完成（awaitTermination）
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;

        // shutdown操作中涉及大量的资源访问和更新，直接通过互斥锁防并发
        mainLock.lock();
        try {
            // 用于shutdown/shutdownNow时的安全访问权限
            checkShutdownAccess();
            // 将线程池状态从RUNNING推进到SHUTDOWN
            advanceRunState(SHUTDOWN);
            // shutdown不会立即停止所有线程，而仅仅先中断idle状态的多余线程进行回收，还在执行任务的线程就慢慢等其执行完
            interruptIdleWorkers();
            // 单独为ScheduledThreadPoolExecutor开的一个钩子函数（hook for ScheduledThreadPoolExecutor）
            onShutdown();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }

    /**
     * 立即关闭线程池（不再接收新任务，工作队列中未完成的任务会以列表的形式返回）
     * @return 当前工作队列中未完成的任务
     * */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;

        final ReentrantLock mainLock = this.mainLock;

        // shutdown操作中涉及大量的资源访问和更新，直接通过互斥锁防并发
        mainLock.lock();
        try {
            // 用于shutdown/shutdownNow时的安全访问权限
            checkShutdownAccess();
            // 将线程池状态从RUNNING推进到STOP
            advanceRunState(STOP);
            interruptWorkers();

            // 将工作队列中未完成的任务提取出来(会清空线程池的workQueue)
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    /**
     * shutdown以及之后的状态，都认为是shutdown
     * 除了RUNNING状态，isShutdown方法都会返回true
     * */
    public boolean isShutdown() {
        return !isRunning(ctl.get());
    }

    /**
     * 线程池是否是正在终止的过程中
     * */
    public boolean isTerminating() {
        int currentCtl = ctl.get();
        // 线程池状态不是RUNNING，但又小于TERMINATED
        return !isRunning(currentCtl) && runStateLessThan(currentCtl, TERMINATED);
    }

    /**
     * 线程池是否已终止
     * */
    public boolean isTerminated() {
        // 线程池状态大于等于TERMINATED
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    /**
     * 调用者会被阻塞，直到线程池变为shutdown状态
     * 通过参数可以控制被阻塞等待的超时时间
     * @throws InterruptedException awaitNanos监听条件变量时，可能会抛出InterruptedException
     * @return 返回true，说明在指定的超时时间内，线程池终止了
     *         返回false，说明在指定的超时时间内，线程池没有终止，提前返回了
     * */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        // 超时时间统一转为纳秒
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                // 已经是终止状态了，直接返回true
                if (runStateAtLeast(ctl.get(), TERMINATED)){
                    return true;
                }
                // 上面的if条件不满足，说明还未终结，但nanos已经减为0了，说明已经超时了
                // 返回false，代表直到超时，线程池依然没有终止
                if (nanos <= 0) {
                    return false;
                }
                // 等待在termination条件变量上（线程池终止时会signAll该条件变量）
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 启动worker工作线程
     * */
    private void runWorker(MyWorker myWorker) {
        // 时worker线程的run方法调用的，此时的current线程的是worker线程
        Thread workerThread = Thread.currentThread();

        Runnable task = myWorker.firstTask;
        // 已经暂存了firstTask，将其清空（有地方根据firstTask是否存在来判断工作线程中负责的任务是否是新提交的）
        myWorker.firstTask = null;

        // 将state由初始化时的-1设置为0
        // 标识着此时当前工作线程开始工作了，这样可以被interruptIfStarted选中
        myWorker.unlock();

        // 默认线程是由于中断退出的
        boolean completedAbruptly = true;
        try {
            // worker线程处理主循环，核心逻辑
            while (task != null || (task = getTask()) != null) {
                // 将state由0标识为1，代表着其由idle状态变成了正在工作的状态
                // 这样interruptIdleWorkers中的tryLock会失败，这样工作状态的线程就不会被该方法中断任务的正常执行
                myWorker.lock();

                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                // 1.如果线程池在关闭状态，确保当前线程是处于已中断状态的
                // 2.如果线程池不在关闭状态，确保当前线程是未中断的
                // 第二种情况，需要再次检查线程池状态来处理清楚中断标识时，与shutdownNow方法并发竞争的竞争场景

                // 情况1：poolStatus >= STOP && !workerThread.isInterrupted()
                // 即线程池状态已经至少是STOP了，且当前工作线程是未中断的状态，此时workerThread.interrupt()
                // 保证上面的第一点: 如果线程池在关闭状态，确保当前线程在执行任务前是处于已中断状态的
                // 情况2：检查的一瞬间poolStatus < STOP，那么大概率是RUNNING状态，那么通过Thread.interrupted()清空当前线程的中断状态
                // 保证上面的第二点：如果线程池不在关闭状态，确保当前线程在执行任务前是未中断的（）
                // 但是有一个特殊情况，即Thread.interrupted()清除中断标识的临界点，另一个线程可能恰好并发的调用了shutdownNow方法，将状态设置为了STOP
                // 情况2又有两个子分支情况
                // 情况2.1：Thread.interrupted()判断返回false，说明当前线程之前没有被中断，当前线程无需中断，interrupted方法返回false，不走if逻辑
                // 情况2.2：Thread.interrupted()判断返回true，说明当前线程之前已被中断，则有可能是恰好刚才由shutdownNow触发的（当然也有可能是别的原因）
                // 需要再检查一下线程池状态，如果大于等于STOP说明线程池要停止了，走if逻辑中断工作线程。
                // 如果线程池状态不是大于等于STOP，则Thread.interrupted()会把中断状态给clear清除掉
                if ((runStateAtLeast(ctl.get(), STOP) || (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP)))
                        && !workerThread.isInterrupted()) {
                    // 触发线程中断，让task.run方法内的线程及时的感知到，从而退出，进而销毁线程
                    workerThread.interrupt();
                }

                try {
                    // 任务执行前的钩子函数
                    beforeExecute(workerThread, task);
                    Throwable thrown = null;
                    try {
                        // 拿到的任务开始执行
                        task.run();
                    } catch (RuntimeException | Error x) {
                        // 使用thrown收集抛出的异常，传递给afterExecute
                        thrown = x;
                        // 同时抛出错误，从而中止主循环
                        throw x;
                    } catch (Throwable x) {
                        // 使用thrown收集抛出的异常，传递给afterExecute
                        thrown = x;
                        // 同时抛出错误，从而中止主循环
                        throw new Error(x);
                    } finally {
                        // 任务执行后的钩子函数，如果任务执行时抛出了错误/异常，thrown不为null
                        afterExecute(task, thrown);
                    }
                } finally {
                    // 将task设置为null,令下一次while循环通过getTask获得新任务
                    task = null;
                    // 无论执行时是否存在异常，已完成的任务数加1
                    myWorker.completedTasks++;
                    // 无论如何
                    myWorker.unlock();
                }

            }
            // getTask返回了null，说明没有可执行的任务或者因为idle超时、线程数超过配置等原因需要回收当前线程。
            // 线程正常的退出，completedAbruptly为false
            completedAbruptly = false;
        }finally {
            // getTask返回null，线程正常的退出，completedAbruptly值为false
            // task.run()执行时抛出了异常/错误，直接跳出了主循环，此时completedAbruptly为初始化时的默认值true
            processWorkerExit(myWorker, completedAbruptly);

            // processWorkerExit执行完成后，worker线程对应的run方法(run->runWorker)也会执行完毕
            // 此时线程对象会进入终止态，等待操作系统回收
            // 而且processWorkerExit方法内将传入的Worker从workers集合中移除，jvm中的对象也会因为不再被引用而被GC回收
            // 此时，当前工作线程所占用的所有资源都已释放完毕
        }
    }

    /**
     * 尝试着从阻塞队列里获得待执行的任务
     * @return 返回null代表工作队列为空，没有需要执行的任务; 或者当前worker线程满足了需要退出的一些条件
     *         返回对应的任务
     * */
    private Runnable getTask() {
        boolean timedOut = false;

        for(;;) {
            int currentCtl = ctl.get();
            int runState = runStateOf(currentCtl);

            // 这里用于控制调用了shutdown方法和shutdownNow方法后，退出当前线程不再处理任务
            // addWorker方法中基于runState状态判断，保证了不再处理新任务，在getTask中保证原有的工作线程逐步有序退出销毁
            // 两种情况走if逻辑,令当前工作线程退出
            // 1 首先runState >= SHUTDOWN，即已经调用过了shutdown或者shutdownNow方法
            // 2.1 如果runState >= STOP，说明是是调用了shutdownNow方法，则无论当前工作队列还有没有未完成的任务都直接退出
            // 2.2 如果runState < STOP,说明是调用了shutdown方法，则依然需要把工作队列中的任务处理完才能退出
            //     所以直到workQueue为空，才退出当前工作线程
            if (runState >= SHUTDOWN && (runState >= STOP || workQueue.isEmpty())) {
                // 当前工作线程需要退出，先将worker计数器减一
                decrementWorkerCount();
                // 返回null，令当前worker线程退出
                return null;
            }

            // 获得当前工作线程个数
            int workCount = workerCountOf(currentCtl);

            // 有两种情况需要指定超时时间的方式从阻塞队列workQueue中获取任务（即timed为true）
            // 1.线程池配置参数allowCoreThreadTimeOut为true，即允许核心线程在idle一定时间后被销毁
            //   所以allowCoreThreadTimeOut为true时，需要令timed为true，这样可以让核心线程也在一定时间内获取不到任务(idle状态)而被销毁
            // 2.线程池配置参数allowCoreThreadTimeOut为false,但当前线程池中的线程数量workCount大于了指定的核心线程数量corePoolSize
            //   说明当前有一些非核心的线程正在工作，而非核心的线程在idle状态一段时间后需要被销毁
            //   所以此时也令timed为true，让这些线程在keepAliveTime时间内由于队列为空拉取不到任务而返回null，将其销毁
            boolean timed = allowCoreThreadTimeOut || workCount > corePoolSize;

            // 有共四种情况不需要往下执行，代表
            // 1 (workCount > maximumPoolSize && workCount > 1)
            // 当前工作线程个数大于了指定的maximumPoolSize（可能是由于启动后通过setMaximumPoolSize调小了maximumPoolSize的值）
            // 已经不符合线程池的配置参数约束了，要将多余的工作线程回收掉
            // 且当前workCount > 1说明存在不止一个工作线程，意味着即使将当前工作线程回收后也还有其它工作线程能继续处理工作队列里的任务，直接返回null表示自己需要被回收

            // 2 (workCount > maximumPoolSize && workCount <= 1 && workQueue.isEmpty())
            // 当前工作线程个数大于了指定的maximumPoolSize（maximumPoolSize被设置为0了）
            // 已经不符合线程池的配置参数约束了，要将多余的工作线程回收掉
            // 但此时workCount<=1，说明将自己这个工作线程回收掉后就没有其它工作线程能处理工作队列里剩余的任务了
            // 所以即使maximumPoolSize设置为0，也需要等待任务被处理完，工作队列为空之后才能回收当前线程，否则还会继续拉取剩余任务

            // 3 (workCount <= maximumPoolSize && (timed && timedOut) && workCount > 1)
            // workCount <= maximumPoolSize符合要求
            // 但是timed && timedOut，说明timed判定命中，需要以poll的方式指定超时时间，并且最近一次拉取任务超时了timedOut=true
            // 进入新的一次循环后timed && timedOut成立，说明当前worker线程处于idle状态等待任务超过了规定的keepAliveTime时间,需要回收当前线程
            // 且当前workCount > 1说明存在不止一个工作线程，意味着即使将当前工作线程回收后也还有其它工作线程能继续处理工作队列里的任务，直接返回null表示自己需要被回收

            // 4 (workCount <= maximumPoolSize && (timed && timedOut) && workQueue.isEmpty())
            // workCount <= maximumPoolSize符合要求
            // 但是timed && timedOut，说明timed判定命中，需要以poll的方式指定超时时间，并且最近一次拉取任务超时了timedOut=true
            // 进入新的一次循环后timed && timedOut成立，说明当前worker线程处于idle状态等待任务超过了规定的keepAliveTime时间,需要回收当前线程
            // 但此时workCount<=1，说明将自己这个工作线程回收掉后就没有其它工作线程能处理工作队列里剩余的任务了
            // 所以即使timed && timedOut超时逻辑匹配，也需要等待任务被处理完，工作队列为空之后才能回收当前线程，否则还会继续拉取剩余任务
            if ((workCount > maximumPoolSize || (timed && timedOut))
                    && (workCount > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(currentCtl)) {
                    // 满足上述条件，说明当前线程需要被销毁了，返回null
                    return null;
                }

                // compareAndDecrementWorkerCount方法由于并发的原因cas执行失败，continue循环重试
                continue;
            }

            try {
                // 根据上面的逻辑的timed标识，决定以什么方式从阻塞队列中获取任务
                Runnable r = timed ?
                        // timed为true，通过poll方法指定获取任务的超时时间（如果指定时间内没有队列依然为空，则返回）
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        // timed为false，通过take方法无限期的等待阻塞队列中加入新的任务
                        workQueue.take();
                if (r != null) {
                    // 获得了新的任务，getWork正常返回对应的任务对象
                    return r;
                }else{
                    // 否则说明timed=true，且poll拉取任务时超时了
                    timedOut = true;
                }
            } catch (InterruptedException retry) {
                // poll or take任务等待时worker线程被中断了，捕获中断异常
                // timeout = false,标识拉取任务时没有超时
                timedOut = false;
            }
        }
    }

    /**
     * 处理worker线程退出
     * @param myWorker 需要退出的工作线程对象
     * @param completedAbruptly 是否是因为中断异常的原因，而需要回收
     * */
    private void processWorkerExit(MyWorker myWorker, boolean completedAbruptly) {
        if (completedAbruptly) {
            // 如果completedAbruptly=true，说明是任务在run方法执行时出错导致的线程退出
            // 而正常退出时completedAbruptly=false，在getTask中已经将workerCount的值减少了
            decrementWorkerCount();
        }

        ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 线程池全局总完成任务数累加上要退出的工作线程已完成的任务数
            this.completedTaskCount += myWorker.completedTasks;
            // workers集合中将当前工作线程剔除
            workers.remove(myWorker);

            // completedTaskCount是long类型的，workers是HashSet，
            // 都是非线程安全的，所以在mainLock的保护进行修改
        } finally {
            mainLock.unlock();
        }

        // 任何一个线程退出时，都尝试一下当前是否满足线程池中止的条件
        tryTerminate();

        int currentCtl = this.ctl.get();
        // 判断当前线程池是否未处于STOP状态(RUNNING状态：正常运行或者是SHUTDOWN：调用shutdown方法，等待工作队列中的任务被执行完)
        if (runStateLessThan(currentCtl, STOP)) {
            // 线程池还未推进到STOP状态
            if (!completedAbruptly) {
                // completedAbruptly=false，说明不是因为中断异常而退出的
                // min标识当前线程池允许的最小线程数量
                // 1 如果allowCoreThreadTimeOut为true，则核心线程也可以被销毁，min=0
                // 2 如果allowCoreThreadTimeOut为false，则min应该为所允许的核心线程个数，min=corePoolSize
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty()) {
                    // 如果min为0了，但工作队列不为空，则修正min=1，因为至少需要一个工作线程来将工作队列中的任务消费、处理掉
                    min = 1;
                }
                if (workerCountOf(currentCtl) >= min) {
                    // 如果当前工作线程数大于了min，当前线程数量是足够的，直接返回（否则要执行下面的addWorker恢复）
                    return;
                }
            }
            // 两种场景会走到这里进行addWorker操作
            // 1 completedAbruptly=true,说明线程是因为中断异常而退出的，需要重新创建一个新的工作线程
            // 2 completedAbruptly=false,且上面的workerCount<min，则说明当前工作线程数不够，需要创建一个
            // 为什么参数core传的是false呢？
            // 因为completedAbruptly=true而中断退出的线程，无论当前工作线程数是否大于核心线程，都需要创建一个新的线程来代替原有的被退出的线程
            addWorker(null, false);
        }
    }

    /**
     * 向线程池中加入worker
     * */
    private boolean addWorker(Runnable firstTask, boolean core) {
        // retry标识外层循环
        retry:
        for (;;) {
            int currentCtl = ctl.get();
            int runState = runStateOf(currentCtl);

            // Check if queue empty only if necessary.
            // 线程池终止时需要返回false,避免新的worker被创建
            // 1 先判断runState >= SHUTDOWN
            // 2 runState >= SHUTDOWN时，意味着不再允许创建新的工作线程，但有一种情况例外
            // 即SHUTDOWN状态下(runState == SHUTDOWN)，工作队列不为空(!workQueue.isEmpty())，还需要继续执行
            // 比如在当前存活的线程发生中断异常时，会调用processWorkerExit方法，在销毁原有工作线程后调用addWorker重新生产一个新的（firstTask == null）
            if (runState >= SHUTDOWN && !(runState == SHUTDOWN && firstTask == null && !workQueue.isEmpty())) {
                // 线程池已经是关闭状态了，不再允许创建新的工作线程，返回false
                return false;
            }

            // 用于cas更新workerCount的内层循环（注意这里面与jdk的写法不同，改写成了逻辑一致但更可读的形式）
            for (;;) {
                // 判断当前worker数量是否超过了限制
                int workerCount = workerCountOf(currentCtl);
                if (workerCount > CAPACITY) {
                    // 当前worker数量超过了设计上允许的最大限制
                    return false;
                }
                if (core) {
                    // 创建的是核心线程，判断当前线程数是否已经超过了指定的核心线程数
                    if (workerCount > this.corePoolSize) {
                        // 超过了核心线程数，创建核心worker线程失败
                        return false;
                    }
                } else {
                    // 创建的是非核心线程，判断当前线程数是否已经超过了指定的最大线程数
                    if (workerCount > this.maximumPoolSize) {
                        // 超过了最大线程数，创建非核心worker线程失败
                        return false;
                    }
                }

                // cas更新workerCount的值
                boolean casSuccess = compareAndIncrementWorkerCount(currentCtl);
                if (casSuccess) {
                    // cas成功，跳出外层循环
                    break retry;
                }

                // 重新检查一下当前线程池的状态与之前是否一致
                currentCtl = ctl.get();  // Re-read ctl
                if (runStateOf(currentCtl) != runState) {
                    // 从外层循环开始continue（因为说明在这期间 线程池的工作状态出现了变化，需要重新判断）
                    continue retry;
                }

                // compareAndIncrementWorkerCount方法cas争抢失败，重新执行内层循环
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;

        MyWorker newWorker = null;
        try {
            // 创建一个新的worker
            newWorker = new MyWorker(firstTask);
            final Thread myWorkerThread = newWorker.thread;
            if (myWorkerThread != null) {
                // MyWorker初始化时内部线程创建成功

                // 加锁，防止并发更新
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();

                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int runState = runStateOf(ctl.get());

                    // 重新检查线程池运行状态，满足以下两个条件的任意一个才创建新Worker
                    // 1 runState < SHUTDOWN
                    // 说明线程池处于RUNNING状态正常运行，可以创建新的工作线程
                    // 2 runState == SHUTDOWN && firstTask == null
                    // 说明线程池调用了shutdown，但工作队列不为空，依然需要新的Worker。
                    // firstTask == null标识着其不是因为外部提交新任务而创建新Worker，而是在消费SHUTDOWN前已提交的任务
                    if (runState < SHUTDOWN ||
                            (runState == SHUTDOWN && firstTask == null)) {
                        if (myWorkerThread.isAlive()) {
                            // 预检查线程的状态，刚初始化的worker线程必须是未唤醒的状态
                            throw new IllegalThreadStateException();
                        }

                        // 加入worker集合
                        this.workers.add(newWorker);

                        int workerSize = workers.size();
                        if (workerSize > largestPoolSize) {
                            // 如果当前worker个数超过了之前记录的最大存活线程数，将其更新
                            largestPoolSize = workerSize;
                        }

                        // 创建成功
                        workerAdded = true;
                    }
                } finally {
                    // 无论是否发生异常，都先将主控锁解锁
                    mainLock.unlock();
                }

                if (workerAdded) {
                    // 加入成功，启动worker线程
                    myWorkerThread.start();
                    // 标识为worker线程启动成功，并作为返回值返回
                    workerStarted = true;
                }
            }
        }finally {
            if (!workerStarted) {
                addWorkerFailed(newWorker);
            }
        }

        return workerStarted;
    }

    /**
     * 根据指定的拒绝处理器，执行拒绝策略
     * */
    private void reject(Runnable command) {
        this.handler.rejectedExecution(command, this);
    }

    /**
     * 当创建worker出现异常失败时，对之前的操作进行回滚
     * 1 如果新创建的worker加入了workers集合，将其移除
     * 2 减少记录存活的worker个数
     * 3 检查线程池是否满足中止的状态，防止这个存活的worker线程阻止线程池的中止
     */
    private void addWorkerFailed(MyWorker myWorker) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (myWorker != null) {
                // 如果新创建的worker加入了workers集合，将其移除
                workers.remove(myWorker);
            }
            // 减少存活的worker个数
            decrementWorkerCount();

            // 尝试着将当前worker线程终止(addWorkerFailed由工作线程自己调用)
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 中断所有处于idle状态的线程
     * */
    private void interruptIdleWorkers() {
        // 默认打断所有idle状态的工作线程
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /**
     * 中断处于idle状态的线程
     * @param onlyOne 如果为ture，至多只中断一个工作线程(可能一个都不中断)
     *                如果为false，中断workers内注册的所有工作线程
     * */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (MyWorker w : workers) {
                Thread t = w.thread;
                // 1. t.isInterrupted()，说明当前线程存在中断信号，之前已经被中断了，无需再次中断
                // 2. w.tryLock(), runWorker方法中如果工作线程获取到任务开始工作，会先进行Lock加锁
                // 则这里的tryLock会加锁失败，返回false。 而返回true的话，就说明当前工作线程是一个idle线程，需要被中断
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        // tryLock成功时，会将内部state的值设置为1，通过unlock恢复到未加锁的状态
                        w.unlock();
                    }
                }
                if (onlyOne) {
                    // 参数onlyOne为true，至多只中断一个工作线程
                    // 即使上面的t.interrupt()没有执行，也在这里跳出循环
                    break;
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * shutdownNow方法内，立即终止线程池时该方法被调用
     * 中断通知所有已经启动的工作线程（比如等待在工作队列上的idle工作线程，或者run方法内部await、sleep等，令其抛出中断异常快速结束）
     * */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (MyWorker w : workers) {
                // 遍历所有的worker线程，已启动的工作线程全部调用Thread.interrupt方法，发出中断信号
                w.interruptIfStarted();
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 用于shutdown/shutdownNow时的安全访问权限
     * 检查当前调用者是否有权限去通过interrupt方法去中断对应工作线程
     * */
    private void checkShutdownAccess() {
        // 判断jvm启动时是否设置了安全管理器SecurityManager
        SecurityManager security = System.getSecurityManager();
        // 如果没有设置，直接返回无事发生

        if (security != null) {
            // 设置了权限管理器，验证当前调用者是否有modifyThread的权限
            // 如果没有，checkPermission会抛出SecurityException异常
            security.checkPermission(shutdownPerm);

            // 通过上述校验，检查工作线程是否能够被调用者访问
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (MyWorker w : workers) {
                    // 检查每一个工作线程中的thread对象是否有权限被调用者访问
                    security.checkAccess(w.thread);
                }
            } finally {
                mainLock.unlock();
            }
        }
    }

    private void advanceRunState(int targetState) {
        for(;;){
            // 获得当前的线程池状态
            int currentCtl = this.ctl.get();

            // 1 （runState >= targetState）如果当前线程池状态不比传入的targetState小
            // 代表当前状态已经比参数要制定的更加快(或者至少已经处于对应阶段了)，则无需更新poolStatus的状态(或语句中第一个条件为false，直接break了)
            // 2  (this.ctl.compareAndSet)，cas的将runState更新为targetState
            // 如果返回true则说明cas更新成功直接break结束（或语句中第一个条件为false，第二个条件为true）
            // 如果返回false说明cas争抢失败，再次进入while循环重试（或语句中第一个和第二个条件都是false，不break而是继续执行循环重试）
            if (runStateAtLeast(currentCtl, targetState) ||
                    this.ctl.compareAndSet(
                            currentCtl,
                            ctlOf(targetState, workerCountOf(currentCtl)
                            ))) {
                break;
            }
        }
    }

    /**
     * 将工作队列中的任务全部转移出来
     * 用于shutdownNow紧急关闭线程池时将未完成的任务返回给调用者，避免任务丢失
     * */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> queue = this.workQueue;
        ArrayList<Runnable> taskList = new ArrayList<>();
        queue.drainTo(taskList);
        // 通常情况下，普通的阻塞队列的drainTo方法可以一次性的把所有元素都转移到taskList中
        // 但jdk的DelayedQueue或者一些自定义的阻塞队列，drainTo方法无法转移所有的元素
        // （比如DelayedQueue的drainTo方法只能转移已经不需要延迟的元素，即getDelay()<=0）
        if (!queue.isEmpty()) {
            // 所以在这里打一个补丁逻辑：如果drainTo方法执行后工作队列依然不为空，则通过更基础的remove方法把队列中剩余元素一个一个的循环放到taskList中
            for (Runnable r : queue.toArray(new Runnable[0])) {
                if (queue.remove(r)) {
                    taskList.add(r);
                }
            }
        }
        return taskList;
    }

    /**
     * 尝试判断是否满足线程池中止条件，如果满足条件，将其推进到最后的TERMINATED状态
     * 注意：必须在任何可能触发线程池中止的场景下调用（例如工作线程退出，或者SHUTDOWN状态下队列工作队列为空等）
     * */
    final void tryTerminate() {
        for (;;) {
            int currentCtl = this.ctl.get();
            if (isRunning(currentCtl)
                    || runStateAtLeast(currentCtl, TIDYING)
                    || (runStateOf(currentCtl) == SHUTDOWN && !workQueue.isEmpty())) {
                // 1 isRunning(currentCtl)为true，说明线程池还在运行中，不满足中止条件
                // 2 当前线程池状态已经大于等于TIDYING了，说明之前别的线程可能已经执行过tryTerminate，且通过了这个if校验，不用重复执行了
                // 3 当前线程池是SHUTDOWN状态，但工作队列中还有任务没处理完，也不满足中止条件
                // 以上三个条件任意一个满足即直接提前return返回
                return;
            }

            // 有两种场景会走到这里
            // 1 执行了shutdown方法(runState状态为SHUTDOWN)，且当前工作线程已经空了
            // 2 执行了shutdownNow方法(runState状态为STOP)
            // 这个时候需要令所有的工作线程都主动的退出来回收资源
            if (workerCountOf(currentCtl) != 0) {
                // 如果当前工作线程个数不为0，说明还有别的工作线程在工作中。
                // 通过interruptIdleWorkers(true)，打断其中的一个idle线程，尝试令其也执行runWorker中的processWorkerExit逻辑，并执行tryTerminate
                // 被中断的那个工作线程也会执行同样的逻辑（getTask方法返回->processWorkerExit->tryTerminate）
                // 这样可以一个接着一个的不断打断每一个工作线程，令其逐步的退出（比起一次性的通知所有的idle工作线程，这样相对平滑很多）
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            // 线程池状态runState为SHUTDOWN或者STOP，且存活的工作线程个数已经为0了
            // 虽然前面的interruptIdleWorkers是一个一个中断idle线程的，但实际上有的工作线程是因为别的原因退出的（恰好workerCountOf为0了）
            // 所以这里是可能存在并发的，因此通过mainLock加锁防止并发，避免重复的terminated方法调用和termination.signalAll方法调用
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // cas的设置ctl的值为TIDYING+工作线程个数0（防止与别的地方ctl并发更新）
                if (ctl.compareAndSet(currentCtl, ctlOf(TIDYING, 0))) {
                    try {
                        // cas成功，调用terminated钩子函数
                        terminated();
                    } finally {
                        // 无论terminated钩子函数是否出现异常
                        // cas的设置ctl的值为TERMINATED最终态+工作线程个数0（防止与别的地方ctl并发更新）
                        ctl.set(ctlOf(TERMINATED, 0));
                        // 通知使用awaitTermination方法等待线程池关闭的其它线程（通过termination.await等待）
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }

            // 如果上述对ctl变量的cas操作失败了，则进行重试，再来一次循环
            // else retry on failed CAS
        }
    }

    // ===================== 留给子类做拓展的方法 =======================
    /**
     * 任务执行前
     * */
    protected void beforeExecute(Thread t, Runnable r) {
        // 默认为无意义的空方法
    }

    /**
     * 任务执行后
     * */
    protected void afterExecute(Runnable r, Throwable t) {
        // 默认为无意义的空方法
    }

    /**
     * 单独为jdk的ScheduledThreadPoolExecutor开的一个钩子函数
     * 由ScheduledThreadPoolExecutor继承ThreadExecutor时重写
     * */
    void onShutdown() {
    }

    /**
     * 线程池实际终止时的钩子函数
     * */
    protected void terminated() { }

    // =============================== worker线程内部类 =====================================
    /**
     * jdk的实现中令Worker继承AbstractQueuedSynchronizer并实现了一个不可重入的锁
     * 1 AQS中的state属性含义
     * -1：标识工作线程还未启动
     *  0：标识工作线程已经启动，但没有开始处理任务(可能是在等待任务，idle状态)
     *  1：标识worker线程正在执行任务（runWorker中，成功获得任务后，通过lock方法将state设置为1）
     * 2
     * */
    private final class MyWorker extends AbstractQueuedSynchronizer implements Runnable{

        final Thread thread;
        Runnable firstTask;
        volatile long completedTasks;

        public MyWorker(Runnable firstTask) {
            // Worker初始化时，state设置为-1，用于interruptIfStarted方法中作为过滤条件，避免还未开始启动的Worker响应中断
            // 在runWorker方法中会通过一次unlock将state修改为0
            setState(-1);

            this.firstTask = firstTask;

            // newThread可能是null
            this.thread = getThreadFactory().newThread(this);
        }

        @Override
        public void run() {
            runWorker(this);
        }

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock(){
            acquire(1);
        }

        public boolean tryLock(){
            return tryAcquire(1);
        }

        public void unlock(){
            release(1);
        }

        public boolean isLocked(){
            return isHeldExclusively();
        }

        void interruptIfStarted() {
            Thread t;
            // 三个条件同时满足，才去中断Worker对应的thread
            // getState() >= 0,用于过滤还未执行runWorker的，刚入队初始化的Worker
            // thread != null，用于过滤掉构造方法中ThreadFactory.newThread返回null的Worker
            // !t.isInterrupted()，用于过滤掉那些已经被其它方式中断的Worker线程(比如用户自己去触发中断，提前终止线程池中的任务)
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    // ============================== jdk默认提供的4种拒绝策略 ===============================================
    /**
     * 抛出RejectedExecutionException的拒绝策略
     * 评价：能让提交任务的一方感知到异常的策略，比较通用，也是jdk默认的拒绝策略
     * */
    public static class MyAbortPolicy implements MyRejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable command, MyThreadPoolExecutor executor) {
            // 直接抛出异常
            throw new RejectedExecutionException("Task " + command.toString() +
                    " rejected from " + executor.toString());
        }
    }

    /**
     * 令调用者线程自己执行command任务的拒绝策略
     * 评价：在线程池压力过大时，让提交任务的线程自己执行该任务（异步变同步），
     *      能够有效地降低线程池的压力，也不会出现任务丢失，但可能导致整体业务吞吐量大幅降低
     * */
    public static class MyCallerRunsPolicy implements MyRejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable command, MyThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                // 如果当前线程池不是shutdown状态，则令调用者线程自己执行command任务
                command.run();
            }else{
                // 如果已经是shutdown状态了，就什么也不做直接丢弃任务
            }
        }
    }

    /**
     * 直接丢弃任务的拒绝策略
     * 评价：简单的直接丢弃任务，适用于对任务执行成功率要求不高的场合
     * */
    public static class MyDiscardPolicy implements MyRejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable command, MyThreadPoolExecutor executor) {
            // 什么也不做的，直接返回
            // 效果就是command任务被无声无息的丢弃了，没有异常
        }
    }

    /**
     * 丢弃当前工作队列中最早入队的任务，然后将当前任务重新提交
     * 评价：适用于后出现的任务能够完全代替之前任务的场合(追求最终一致性)
     * */
    public static class MyDiscardOldestPolicy implements MyRejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable command, MyThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                // 如果当前线程池不是shutdown状态，则丢弃当前工作队列中最早入队的任务，然后将当前任务重新提交
                executor.getQueue().poll();
                executor.execute(command);
            }else{
                // 如果已经是shutdown状态了，就什么也不做直接丢弃任务
            }
        }
    }
}