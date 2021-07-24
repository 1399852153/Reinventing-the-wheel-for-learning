import spinlock.MCSLock;
import spinlock.SpinLock;
import util.SpinLockTestUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/7/24
 */
public class MCSLockTest {

    public static void main(String[] args) throws InterruptedException {
        SpinLock spinLock = new MCSLock();
        int sumCount = 10;
        int repeatSum = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(sumCount);
        int result = SpinLockTestUtil.testConcurrentSum(executorService, spinLock, sumCount, repeatSum);
        executorService.shutdown();

        System.out.println(result);
        if (sumCount*repeatSum != result) {
            throw new RuntimeException("testMCSLock error: sumCount != result result=" + result);
        }
    }
}
