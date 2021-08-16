import spinlock.CLHLockV2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/7/29
 */
public class CLHLockV2Test {

    public static void main(String[] args) throws InterruptedException {
        CLHLockV2 CLHLockV2 = new CLHLockV2();
        int sumCount = 10;
        int repeatSum = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(sumCount);
        int result = testConcurrentSum(executorService, CLHLockV2, sumCount, repeatSum, 20);
        executorService.shutdown();

        System.out.println(result);
        if (sumCount*repeatSum != result) {
            throw new RuntimeException("testCLHLockV2 error: sumCount != result result=" + result);
        }
    }

    public static int testConcurrentSum(ExecutorService executorService, CLHLockV2 CLHLockV2, int sumCount, int repeatSum, long timeout) throws InterruptedException {
        CountDownLatch driverLatch = new CountDownLatch(1);
        CountDownLatch barrierLatch = new CountDownLatch(sumCount);

        final int[] count = {0};
        for (int i = 0; i < sumCount; i++) {
            executorService.execute(()->{
                try {
                    driverLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                for(int j=0;j<repeatSum;j++) {
                    // 用自旋锁保护自增操作并发时的线程安全
                    while(true){
                        if(CLHLockV2.lock(timeout)){
                            System.out.println("加锁成功");
                            count[0]++;
                            CLHLockV2.unlock();
                            break;
                        }
                    }
                }

                barrierLatch.countDown();
            });
        }

        long start = System.currentTimeMillis();
        driverLatch.countDown();

        barrierLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("total cost=" + (end-start) + "ms");

        return count[0];
    }
}
