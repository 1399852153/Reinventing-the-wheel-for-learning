package threadpool;

public interface MyRejectedExecutionHandler {

    void rejectedExecution(Runnable command, MyThreadPoolExecutor executor);
}
