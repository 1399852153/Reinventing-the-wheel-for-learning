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
        int corePoolSize = 5;
        int taskNum = 20;
        MyThreadPoolExecutorV1 myThreadPoolExecutorV1 = new MyThreadPoolExecutorV1(
                corePoolSize, 10, 60, TimeUnit.SECONDS,
                // 无界队列
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory(),
                new MyThreadPoolExecutorV1.MyAbortPolicy());

        for(int i=0; i<taskNum; i++) {
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
        Assert.assertEquals(corePoolSize, myThreadPoolExecutorV1.getPoolSize());
        Assert.assertEquals(taskNum-corePoolSize, myThreadPoolExecutorV1.getQueue().size());

    }

    /**
     * 有界阻塞队列（队列容量为10）
     * corePoolSize = 5
     * maximumPoolSize = 10
     * 提交20个任务，最多会创建10(maximumPoolSize)个工作线程
     * */
    @Test
    public void testBoundedQueueNoReject() throws InterruptedException {
        int corePoolSize = 5;
        int maximumPooSize = 10;
        int taskNum = 20;
        int queueCapacity = 10;

        MyThreadPoolExecutorV1 myThreadPoolExecutorV1 = new MyThreadPoolExecutorV1(
                corePoolSize, maximumPooSize, 60, TimeUnit.SECONDS,
                // 有界队列
                new LinkedBlockingQueue<>(queueCapacity),
                Executors.defaultThreadFactory(),
                new MyThreadPoolExecutorV1.MyAbortPolicy());

        for(int i=0; i<taskNum; i++) {
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
        Assert.assertEquals(maximumPooSize, myThreadPoolExecutorV1.getPoolSize());
        Assert.assertEquals(queueCapacity, myThreadPoolExecutorV1.getQueue().size());

    }

    /**
     * 有界阻塞队列（队列容量为10）
     * corePoolSize = 5
     * maximumPoolSize = 10
     * 提交21个任务，最多会创建10(maximumPoolSize)个工作线程，最后一个任务提交时触发拒绝策略
     * */
    @Test
    public void testBoundedQueueHasReject() throws InterruptedException {
        int corePoolSize = 5;
        int maximumPooSize = 10;
        int taskNum = 21;
        int queueCapacity = 10;

        MyThreadPoolExecutorV1 myThreadPoolExecutorV1 = new MyThreadPoolExecutorV1(
                corePoolSize, maximumPooSize, 60, TimeUnit.SECONDS,
                // 有界队列
                new LinkedBlockingQueue<>(queueCapacity),
                Executors.defaultThreadFactory(),
                new MyThreadPoolExecutorV1.MyAbortPolicy());

        boolean hasRejectedExecutionException = false;
        try {
            for (int i = 0; i < taskNum; i++) {
                myThreadPoolExecutorV1.execute(() -> {
                    while (true) {
                        System.out.println("666:" + Thread.currentThread().getName());
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
        Assert.assertEquals(maximumPooSize, myThreadPoolExecutorV1.getPoolSize());
        Assert.assertEquals(queueCapacity, myThreadPoolExecutorV1.getQueue().size());
    }

    /**
     * 测试允许核心线程超时
     * 提交5个任务(每个耗时2秒)，会创建5个核心线程
     * 等待10s后idle的核心线程全部退出（keepAliveTime=5s）
     * */
    @Test
    public void testAllowCoreThreadTimeOut() throws InterruptedException {
        MyThreadPoolExecutorV1 myThreadPoolExecutorV1 = new MyThreadPoolExecutorV1(
                5, 10, 5, TimeUnit.SECONDS,
                // 有界队列
                new LinkedBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new MyThreadPoolExecutorV1.MyAbortPolicy());
        // 允许idle的核心线程销毁
        myThreadPoolExecutorV1.allowCoreThreadTimeOut(true);

        for (int i = 0; i < 5; i++) {
            myThreadPoolExecutorV1.execute(() -> {
                // 不是死循环，休眠后结束
                System.out.println("666:" + Thread.currentThread().getName());
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        // 未超时前是5个
        Assert.assertEquals(5, myThreadPoolExecutorV1.getPoolSize());
        Thread.sleep(10000L);
        // 10s后核心线程就都销毁了
        Assert.assertEquals(0, myThreadPoolExecutorV1.getPoolSize());
        Assert.assertEquals(0, myThreadPoolExecutorV1.getQueue().size());
    }
}
