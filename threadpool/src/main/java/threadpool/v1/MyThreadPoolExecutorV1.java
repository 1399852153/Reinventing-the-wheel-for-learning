package threadpool.v1;

import threadpool.MyThreadPoolExecutor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

            // cas并发时争抢失败，添加不成功则继续往下执行
        }

        // v1版本暂时不考虑shutdown时的处理，不判断isRunning状态
        boolean enqueueSuccess = this.workQueue.offer(command);
        if(enqueueSuccess){
            // 成功加入阻塞队列
            if(this.workerCount.get() == 0){
                // 在corePoolSize为0的情况下，不会存在核心线程。
                // 一个任务在入队之后，如果当前线程池中一个线程都没有，则需要创建一个非核心线程来处理入队的任务
                // 因此firstTask为null，目的是先让任务先入队后创建线程去拉取任务并执行
                addWorker(null,false);
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

    private boolean addWorker(Runnable firstTask, boolean core) {
        // todo
        return false;
    }

    final void reject(Runnable command) {
        // todo
    }
}