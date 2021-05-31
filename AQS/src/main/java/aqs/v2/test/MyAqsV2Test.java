package aqs.v2.test;

import aqs.v2.MyCountDownLatch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/5/30
 */
public class MyAqsV2Test {

    /**
     * 令某一线程（主线程）等待其它线程执行完任务后再被唤醒
     * */
    public static void main(String[] args) throws InterruptedException {
        int concurrentNum = 10;

        MyCountDownLatch myCountDownLatch = new MyCountDownLatch(concurrentNum);

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentNum);
        for(int i=0; i<concurrentNum; i++) {
            executorService.submit(() -> {
                System.out.println("doSomething" + " thread=" + Thread.currentThread().getName());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.out.println("doSomething finish" + " thread=" + Thread.currentThread().getName());
                myCountDownLatch.countDown();
            });
        }

        System.out.println("等待分支任务执行完毕");
        myCountDownLatch.await();
        System.out.println("分支任务执行完毕,主线程继续执行");
        executorService.shutdown();
    }


}
