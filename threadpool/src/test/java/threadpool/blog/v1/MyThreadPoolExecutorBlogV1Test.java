package threadpool.blog.v1;

import org.junit.Assert;
import org.junit.Test;
import threadpool.blog.MyThreadPoolExecutorV1;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class MyThreadPoolExecutorBlogV1Test {

    /**
     * 无界阻塞队列
     * corePoolSize = 5
     * maximumPoolSize = 10
     * 提交20个任务，最多只会创建5(corePoolSize)个工作线程
     * */
    @Test
    public void testUnBoundedQueue() throws InterruptedException {
        MyThreadPoolExecutorV1 myThreadPoolExecutorV1 = new MyThreadPoolExecutorV1(
                5, 10, 60, TimeUnit.SECONDS,
                // 无界队列
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory(),
                new MyThreadPoolExecutorV1.MyAbortPolicy());

        for(int i=0; i<20; i++) {
            myThreadPoolExecutorV1.execute(() -> {
                while(true){
                    System.out.println("666:" + Thread.currentThread().getName());
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        Thread.sleep(5000L);
        Assert.assertEquals(5, myThreadPoolExecutorV1.getPoolSize());
    }

    /**
     * 有界阻塞队列（队列容量为10）
     * corePoolSize = 5
     * maximumPoolSize = 10
     * 提交20个任务，最多会创建10(maximumPoolSize)个工作线程
     * */
    @Test
    public void testBoundedQueueNoReject() throws InterruptedException {
        MyThreadPoolExecutorV1 myThreadPoolExecutorV1 = new MyThreadPoolExecutorV1(
                5, 10, 60, TimeUnit.SECONDS,
                // 有界队列
                new LinkedBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new MyThreadPoolExecutorV1.MyAbortPolicy());

        for(int i=0; i<20; i++) {
            myThreadPoolExecutorV1.execute(() -> {
                while(true){
                    System.out.println("666:" + Thread.currentThread().getName());
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        Thread.sleep(5000L);
        Assert.assertEquals(10, myThreadPoolExecutorV1.getPoolSize());
    }

    /**
     * 有界阻塞队列（队列容量为10）
     * corePoolSize = 5
     * maximumPoolSize = 10
     * 提交21个任务，最后一个任务提交时触发拒绝策略
     * */
    @Test
    public void testBoundedQueueHasReject() throws InterruptedException {
        MyThreadPoolExecutorV1 myThreadPoolExecutorV1 = new MyThreadPoolExecutorV1(
                5, 10, 60, TimeUnit.SECONDS,
                // 有界队列
                new LinkedBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new MyThreadPoolExecutorV1.MyAbortPolicy());

        boolean hasRejectedExecutionException = false;
        try {
            for (int i = 0; i < 21; i++) {
                System.out.println("i=" + i);
                myThreadPoolExecutorV1.execute(() -> {
                    while (true) {
//                        System.out.println("666:" + Thread.currentThread().getName());
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }catch (RejectedExecutionException ignore){
            System.out.println("触发拒绝策略");
            hasRejectedExecutionException = true;
        }

        Thread.sleep(5000L);
        Assert.assertTrue(hasRejectedExecutionException);
        Assert.assertEquals(10, myThreadPoolExecutorV1.getPoolSize());

    }
}
