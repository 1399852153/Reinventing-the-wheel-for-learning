package threadpool.v1;

import java.util.concurrent.ThreadFactory;

/**
 * jdk的实现中继承AbstractQueuedSynchronizer，是因为要防止线程在未执行任务时被无效的中断唤醒
 * */
public class MyWorker {

    private final Thread thread;
    private Runnable task;
//    private volatile long completedTasks;


    public MyWorker(ThreadFactory threadFactory, Runnable task) {
        this.task = task;

        // newThread可能是null
        this.thread = threadFactory.newThread(task);
    }

    public Thread getThread() {
        return thread;
    }

    public Runnable getTask() {
        return task;
    }
}
