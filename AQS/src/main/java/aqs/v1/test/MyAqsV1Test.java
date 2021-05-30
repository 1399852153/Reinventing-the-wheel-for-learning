package aqs.v1.test;

import aqs.v1.MyReentrantLockV1;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/5/23
 */
public class MyAqsV1Test {

    public static void main(String[] args) throws InterruptedException {
       testConcurrentAdd(100,10000);
    }

    private static void testConcurrentAdd(int concurrentThreadNum,int repeatNum) throws InterruptedException {
        MyReentrantLockV1 myReentrantLock = new MyReentrantLockV1(true);

        final int[] num = {0};
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreadNum);

        for(int i=0; i<concurrentThreadNum; i++){
            executorService.execute(()-> {
                for(int j=0; j<repeatNum; j++){
                    myReentrantLock.lock();
//                    System.out.println("doAdd thread=" + Thread.currentThread().getName() + " num=" + num[0]);
                    num[0]++;
//                    System.out.println("doAdd over thread=" + Thread.currentThread().getName() + " num=" + num[0]);
                    myReentrantLock.unlock();
                }
            });
        }

        // 简单起见直接休眠一段时间(repeatNum不要太大就行，待优化为countDownLatch)
        Thread.sleep(concurrentThreadNum * 100L);

        System.out.println("" + num[0] + " concurrentThreadNum * repeatNum=" + concurrentThreadNum * repeatNum);
        if(num[0] != concurrentThreadNum * repeatNum){
            throw new RuntimeException("并发add存在问题");
        }

        executorService.shutdown();
    }
}
