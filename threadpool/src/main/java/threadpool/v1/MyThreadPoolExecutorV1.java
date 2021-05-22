package threadpool.v1;

import threadpool.MyThreadPoolExecutor;

import java.util.concurrent.BlockingQueue;

/**
 * @author xiongyx
 * @date 2021/5/7
 */
public class MyThreadPoolExecutorV1 implements MyThreadPoolExecutor {

  private int maxSize;
  private BlockingQueue<Runnable> workerQueue;




  @Override
  public void execute(Runnable command) {

  }

  @Override
  public boolean remove(Runnable task) {
    return false;
  }

  @Override
  public void shutdown() {

  }
}
