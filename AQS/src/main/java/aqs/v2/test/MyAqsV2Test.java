package aqs.v2.test;

import aqs.v2.MyCountDownLatch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/5/30
 */
public class MyAqsV2Test {

    public static void main(String[] args) throws InterruptedException {

        MyCountDownLatch myCountDownLatch = new MyCountDownLatch(1);

        int concurrentNum = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentNum);
        for(int i=0; i<concurrentNum; i++) {
            executorService.submit(() -> {
                System.out.println("准备就绪 预备" + "thread=" + Thread.currentThread().getName());
                try {
                    myCountDownLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.out.println("出发！！！" + "thread=" + Thread.currentThread().getName());
            });
        }

        Thread.sleep(3000);
        myCountDownLatch.countDown();
        System.out.println("发车完毕");
        executorService.shutdown();
    }
}
