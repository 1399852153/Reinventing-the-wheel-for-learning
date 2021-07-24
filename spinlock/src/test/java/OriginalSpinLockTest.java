import spinlock.OriginalSpinLock;
import spinlock.SpinLock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/7/24
 */
public class OriginalSpinLockTest {

    public static void main(String[] args) throws InterruptedException {
        SpinLock spinLock = new OriginalSpinLock();
        int sumCount = 100;
        int repeatSum = 10000;
        ExecutorService executorService = Executors.newFixedThreadPool(sumCount);
        int result = SpinLockTestUtil.testConcurrentSum(executorService, spinLock, sumCount, repeatSum);
        executorService.shutdown();

        System.out.println(result);
        if (sumCount*repeatSum != result) {
            throw new RuntimeException("OriginalSpinLock error: sumCount != result result=" + result);
        }
    }
}
