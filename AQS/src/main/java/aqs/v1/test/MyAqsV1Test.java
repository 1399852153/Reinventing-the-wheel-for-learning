package aqs.v1.test;

import aqs.v1.MyReentrantLock;

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
        MyReentrantLock myReentrantLock = new MyReentrantLock(false);

        final int[] num = {0};
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreadNum);

        for(int i=0; i<concurrentThreadNum; i++){
            executorService.execute(()-> {
                for(int j=0; j<repeatNum; j++){
                    myReentrantLock.lock();
                    System.out.println("doAdd thread=" + Thread.currentThread().getName() + " num=" + num[0]);
                    num[0]++;
                    System.out.println("doAdd over thread=" + Thread.currentThread().getName() + " num=" + num[0]);
                    myReentrantLock.unlock();
                }
            });
        }

        // 简单起见直接休眠一段时间(repeatNum不要太大就行)
        Thread.sleep(concurrentThreadNum * 10L);

        assert (num[0] == concurrentThreadNum * repeatNum);

        System.out.println(num[0]);
        executorService.shutdown();
    }
}
