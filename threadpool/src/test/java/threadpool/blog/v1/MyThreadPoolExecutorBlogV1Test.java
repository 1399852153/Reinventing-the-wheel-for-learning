package threadpool.blog.v1;

import org.junit.Test;
import threadpool.blog.MyThreadPoolExecutorV1;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MyThreadPoolExecutorBlogV1Test {

    /**
     * 无界阻塞队列
     * corePoolSize = 5
     * maximumPoolSize = 10
     * 最多只会创建5(corePoolSize)个工作线程
     * */
    @Test
    public void testUnBoundedQueue() throws InterruptedException {
        MyThreadPoolExecutorV1 myThreadPoolExecutorV1 = new MyThreadPoolExecutorV1(
                5, 10, 30, TimeUnit.SECONDS,
                // 无界队列
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory(),
                new MyThreadPoolExecutorV1.MyAbortPolicy());

        for(int i=0; i<20; i++) {
            myThreadPoolExecutorV1.execute(() -> {
                while(true){
                    System.out.println("666:" + Thread.currentThread().getName());
                    try {
                        Thread.sleep(3000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        new CountDownLatch(1).await();
    }
}
