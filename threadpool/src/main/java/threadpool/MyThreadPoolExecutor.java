package threadpool;

import java.util.concurrent.BlockingQueue;

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
     * 获得当前线程池的工作队列
     * */
    BlockingQueue<Runnable> getQueue();
}
