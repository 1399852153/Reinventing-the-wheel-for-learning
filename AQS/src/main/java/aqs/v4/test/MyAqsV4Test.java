package aqs.v4.test;

import aqs.v2.MyCountDownLatch;
import aqs.v4.MyReentrantLockV4;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/5/23
 */
public class MyAqsV4Test {

    private static volatile int num = 0;
    public static void main(String[] args) throws InterruptedException {
       testConcurrentAdd(30,1000);
    }

    private static void testConcurrentAdd(int concurrentThreadNum,int repeatNum) throws InterruptedException {
        MyReentrantLockV4 myReentrantLock = new MyReentrantLockV4(true);
        MyCountDownLatch countDownLatch = new MyCountDownLatch(concurrentThreadNum);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreadNum);

        for(int i=0; i<concurrentThreadNum; i++){
            executorService.execute(()-> {
                for(int j=0; j<repeatNum; j++){
                    myReentrantLock.lock();
                    num++;
                    myReentrantLock.unlock();
                }
                countDownLatch.countDown();
            });
        }

        countDownLatch.await();

        System.out.println("" + num + " concurrentThreadNum * repeatNum=" + concurrentThreadNum * repeatNum);
        if(num != concurrentThreadNum * repeatNum){
            throw new RuntimeException("并发add存在问题");
        }

        executorService.shutdown();
    }
}
