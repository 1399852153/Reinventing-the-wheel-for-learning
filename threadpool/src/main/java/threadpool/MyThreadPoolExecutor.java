package threadpool;

/**
 * @author xiongyx
 * @date 2021/5/7
 */
public interface MyThreadPoolExecutor {

    /**
     * 提交任务进行执行
     */
    void execute(Runnable command);

    /**
     * 移除一个任务
     */
    boolean remove(Runnable task);

    /**
     * 关闭线程池（不再接收新任务，但已提交的任务会全部被执行）
     * 但不会等待任务彻底的执行完成（awaitTermination）
     */
    void shutdown();

}
