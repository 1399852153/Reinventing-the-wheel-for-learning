import spinlock.SpinLock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * @author xiongyx
 * @date 2021/7/24
 */
public class SpinLockTestUtil {

    public static int testConcurrentSum(ExecutorService executorService, SpinLock spinLock, int sumCount, int repeatSum) throws InterruptedException {

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
                    spinLock.lock();
                    count[0]++;
                    spinLock.unlock();
                }

                barrierLatch.countDown();
            });
        }
        driverLatch.countDown();

        barrierLatch.await();

        return count[0];
    }

}
