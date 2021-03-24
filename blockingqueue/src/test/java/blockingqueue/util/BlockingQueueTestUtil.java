package blockingqueue.util;

import blockingqueue.MyBlockingQueue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/3/24
 */
public class BlockingQueueTestUtil {

    public static void statisticBlockingQueueRuntime(
            MyBlockingQueue<Integer> blockingQueue, int workerNum, int perWorkerProcessNum, int repeatTime) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(workerNum * 2);

        for(int i=0; i<repeatTime; i++){
            long oneTurnTime = oneTurnExecute(executorService,blockingQueue,workerNum,perWorkerProcessNum);
            System.out.println("turn=" + i + " costTime=" + oneTurnTime + "ms");
        }

        executorService.shutdown();
    }

    private static long oneTurnExecute(ExecutorService executorService, MyBlockingQueue<Integer> blockingQueue,
                                       int workerNum, int perWorkerProcessNum) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        CountDownLatch countDownLatch = new CountDownLatch(workerNum * 2);

        // 创建workerNum个生产者/消费者
        for(int i=0; i<workerNum; i++){
            executorService.execute(()->{
                produce(blockingQueue,perWorkerProcessNum);
                countDownLatch.countDown();
            });

            executorService.execute(()->{
                consume(blockingQueue,perWorkerProcessNum);
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        long endTime = System.currentTimeMillis();

        return endTime - startTime;
    }

    private static void produce(MyBlockingQueue<Integer> blockingQueue,int perWorkerProcessNum){
        try {
            // 每个生产者生产perWorkerProcessNum个元素
            for(int j=0; j<perWorkerProcessNum; j++){
                blockingQueue.put(j);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void consume(MyBlockingQueue<Integer> blockingQueue,int perWorkerProcessNum){
        try {
            // 每个消费者消费perWorkerProcessNum个元素
            for(int j=0; j<perWorkerProcessNum; j++){
                blockingQueue.take();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
