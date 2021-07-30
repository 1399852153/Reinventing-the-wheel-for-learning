import spinlock.CLHLockV1;
import spinlock.CLHLockV2;
import spinlock.SpinLock;
import util.SpinLockTestUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/7/29
 */
public class CLHLockV2Test {

    public static void main(String[] args) throws InterruptedException {
        SpinLock spinLock = new CLHLockV2();
        int sumCount = 10;
        int repeatSum = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(sumCount);
        int result = SpinLockTestUtil.testConcurrentSum(executorService, spinLock, sumCount, repeatSum);
        executorService.shutdown();

        System.out.println(result);
        if (sumCount*repeatSum != result) {
            throw new RuntimeException("testCLHLockV2 error: sumCount != result result=" + result);
        }
    }
}
