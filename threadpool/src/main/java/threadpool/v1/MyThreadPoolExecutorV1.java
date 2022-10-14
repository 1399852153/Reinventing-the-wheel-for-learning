package threadpool.v1;

import threadpool.MyRejectedExecutionHandler;
import threadpool.MyThreadPoolExecutor;

import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xiongyx
 * @date 2021/5/7
 */
public class MyThreadPoolExecutorV1 implements MyThreadPoolExecutor {

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
     * 当前线程池中存在的worker线程数量
     */
    private AtomicInteger workerCount;

    /**
     * 维护当前存活的worker线程集合
     * */
    private final HashSet<MyWorker> workers = new HashSet<>();

    /**
     * 主控锁
     * */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Tracks largest attained pool size. Accessed only under mainLock.
     * 跟踪线程池曾经有过的最大线程数量（只能在mainLock的并发保护下更新）
     */
    private int largestPoolSize;

    /**
     * 是否允许核心线程在idle一定时间后被销毁（和非核心线程一样）
     * */
    private volatile boolean allowCoreThreadTimeOut;


    public MyThreadPoolExecutorV1(int corePoolSize,
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

        if (workerCount.get() < corePoolSize) {
            // 如果当前存在的worker线程数量低于指定的核心线程数量，则创建新的核心线程
            boolean addCoreWorkerSuccess = addWorker(command,true);
            if(addCoreWorkerSuccess){
                // 添加成功，直接返回即可
                return;
            }
            // 因为cas并发时争抢失败等原因导致添加核心线程不成功，则继续往下执行
        }

        // v1版本暂时不考虑shutdown时的处理，不判断状态
        boolean enqueueSuccess = this.workQueue.offer(command);
        if(enqueueSuccess){
            // 成功加入阻塞队列
            if(this.workerCount.get() == 0){
                // 在corePoolSize为0的情况下，不会存在核心线程。
                // 一个任务在入队之后，如果当前线程池中一个线程都没有，则需要创建一个非核心线程来处理入队的任务
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
                // 创建非核心线程失败，执行拒绝策略
                reject(command);
            }else{
                // 创建非核心线程成功，成功返回
                return;
            }
        }
    }

    @Override
    public boolean remove(Runnable task) {
        return false;
    }

    @Override
    public void shutdown() {

    }

    public void runWorker(MyWorker myWorker) {
        // 时worker线程的run方法调用的，此时的current线程的是worker线程
        Thread workerThread = Thread.currentThread();
        Runnable task = myWorker.firstTask;

        // todo state设置为0，开启中断
        // myWorker.unlock();

        // 默认线程是由于中断退出的
        boolean completedAbruptly = true;
        try {
            // worker线程处理主循环，核心逻辑
            while (task != null || (task = getTask()) != null) {
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
                }

            }
            // 由于没有可执行的任务了。线程正常的退出
            completedAbruptly = false;
        }finally {
            processWorkerExit(myWorker, completedAbruptly);
        }

    }

    /**
     * 尝试着从阻塞队列里获得待执行的任务
     * @return 返回null代表工作队列为空，没有需要执行的任务; 或者当前worker线程满足了需要退出的一些条件
     *         返回对应的任务
     * */
    private Runnable getTask() {
        boolean timedOut = false;

        while(true) {
            // 工作队列为空
            if (workQueue.isEmpty()) {
                // 当前工作线程需要退出，先将worker计数器减一
                decrementWorkerCount();
                // 返回null，令当前worker线程退出
                return null;
            }

            // 获得当前线程个数
            int workCount = this.workerCount.get();

            // 有两种情况需要指定超时时间的方式从阻塞队列workQueue中获取任务（即timed为true）
            // 1.线程池配置参数allowCoreThreadTimeOut为true，即允许核心线程在idle一定时间后被销毁
            //   所以allowCoreThreadTimeOut为true时，需要令timed为true，这样可以让核心线程也在一定时间内获取不到任务(idle状态)而被销毁
            // 2.线程池配置参数allowCoreThreadTimeOut为false,但当前线程池中的线程数量workCount大于了指定的核心线程数量corePoolSize
            //   说明当前有一些非核心的线程正在工作，而非核心的线程在idle状态一段时间后需要被销毁
            //   所以此时也令timed为true，让这些线程在keepAliveTime时间内由于队列为空拉取不到任务而返回null，将其销毁
            boolean timed = allowCoreThreadTimeOut || workCount > corePoolSize;

            // 有四种情况不需要往下执行，代表
            // todo 待完善
            if ((workCount > maximumPoolSize || (timed && timedOut))
                    && (workCount > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(workCount)) {
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
     * */
    private void processWorkerExit(MyWorker myWorker, boolean completedAbruptly) {
        // todo
    }

    /**
     * 向线程池中加入worker
     * */
    private boolean addWorker(Runnable firstTask, boolean core) {
        while(true) {
            // 判断当前worker数量是否超过了限制
            int workerCount = this.workerCount.get();
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
            boolean casSuccess = compareAndIncrementWorkerCount(workerCount);
            if(casSuccess){
                // cas成功，跳出循环
                break;
            }

            // cas争抢失败，重新循环
        }

        boolean workerStarted = false;
        boolean workerAdded;

        MyWorker newWorker = null;
        try {
            // 创建一个新的worker
            newWorker = new MyWorker(firstTask);
            final Thread myWorkerThread = newWorker.thread;
            // 线程创建成功
            if (myWorkerThread != null) {
                final ReentrantLock mainLock = this.mainLock;

                // 加锁，防止并发更新
                mainLock.lock();

                try {
                    if (myWorkerThread.isAlive()) {
                        // precheck that t is startable
                        // 预检查线程的状态，刚初始化的worker线程必须是未唤醒的状态
                        throw new IllegalThreadStateException();
                    }

                    // 加入worker集合
                    this.workers.add(newWorker);
                    // 创建成功
                    workerAdded = true;

                    int workerSize = workers.size();
                    if (workerSize > largestPoolSize) {
                        // 如果当前worker个数超过了之前记录的最大存活线程数，将其更新
                        largestPoolSize = workerSize;
                    }
                } finally {
                    // 无论是否发生异常，都先将主控锁解锁
                    mainLock.unlock();
                }

                // 加入成功，启动worker线程
                myWorkerThread.start();
                // 标识为worker线程启动成功，并作为返回值返回
                workerStarted = true;
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

    private boolean compareAndIncrementWorkerCount(int expect) {
        return this.workerCount.compareAndSet(expect, expect + 1);
    }
    private boolean compareAndDecrementWorkerCount(int expect) {
        return this.workerCount.compareAndSet(expect, expect - 1);
    }

    private void decrementWorkerCount() {
        do {
            // cas更新，减少workerCount
        } while (!compareAndDecrementWorkerCount(this.workerCount.get()));
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
            // todo
//            if (value) {
//                interruptIdleWorkers();
//            }
        }
    }

    /**
     * 当创建worker出现异常失败时，对之前的操作进行回滚
     * 1 如果新创建的worker加入了workers集合，将其移除
     * 2 减少记录存活的worker个数
     * 3 todo 检查线程池是否满足中止的状态，防止这个存活的worker线程阻止线程池的中止
     */
    private void addWorkerFailed(MyWorker myWorker) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (myWorker != null) {
                workers.remove(myWorker);
            }
            decrementWorkerCount();

            // todo 暂时不考虑shutdown问题
            // tryTerminate();
        } finally {
            mainLock.unlock();
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

    }


    // =============================== worker线程内部类 =====================================
    private final class MyWorker implements Runnable{

        final Thread thread;
        Runnable firstTask;
        volatile long completedTasks;


        public MyWorker(Runnable firstTask) {
            this.firstTask = firstTask;

            // newThread可能是null
            this.thread = getThreadFactory().newThread(this);
        }

        @Override
        public void run() {
            runWorker(this);
        }
    }
}