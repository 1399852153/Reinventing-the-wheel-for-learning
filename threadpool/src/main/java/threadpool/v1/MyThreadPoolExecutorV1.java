package threadpool.v1;

import threadpool.MyThreadPoolExecutor;

import java.util.HashSet;
import java.util.concurrent.*;
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
    private volatile RejectedExecutionHandler handler;

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

    public MyThreadPoolExecutorV1(int corePoolSize,
                                  int maximumPoolSize,
                                  long keepAliveTime,
                                  TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue,
                                  ThreadFactory threadFactory,
                                  RejectedExecutionHandler handler) {
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
            newWorker = new MyWorker(this.threadFactory, firstTask);
            final Thread myWorkerThread = newWorker.getThread();
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

    private void reject(Runnable command) {
        // todo
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
}