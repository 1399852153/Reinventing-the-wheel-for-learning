package threadpool.v1;

/**
 * jdk继承AbstractQueuedSynchronizer，是因为要防止线程在未执行任务时被无效的中断唤醒
 * */
public class MyWorker {

    private final Thread thread;
    private Runnable task;
//    private volatile long completedTasks;


    public MyWorker(Thread thread, Runnable task) {
        this.thread = thread;
        this.task = task;
    }
}
