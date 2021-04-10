package blockingqueue.util;

import blockingqueue.MyBlockingQueue;
import blockingqueue.array.MyArrayBlockingQueueV5;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiongyx
 * @date 2021/3/24
 */
public class BlockingQueueTestUtil {

    public static void main(String[] args) throws InterruptedException {
        MyBlockingQueue<Integer> myArrayBlockingQueueV4 = new MyArrayBlockingQueueV5<>(3);
        long avgCostTimeV4 = BlockingQueueTestUtil.statisticBlockingQueueRuntime(myArrayBlockingQueueV4, 100, 3000, 5);
        System.out.println("avgCostTimeV4=" + avgCostTimeV4);
    }

    public static long statisticBlockingQueueRuntime(
            MyBlockingQueue<Integer> blockingQueue, int workerNum, int perWorkerProcessNum, int repeatTime) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(workerNum * 2);
        // 第一次执行时存在一定的初始化开销，不进行统计
        oneTurnExecute(executorService,blockingQueue,workerNum,perWorkerProcessNum);

        long totalTime = 0;
        for(int i=0; i<repeatTime; i++){
            long oneTurnTime = oneTurnExecute(executorService,blockingQueue,workerNum,perWorkerProcessNum);
            totalTime += oneTurnTime;
        }

        executorService.shutdown();

        assert blockingQueue.isEmpty();

        return totalTime/repeatTime;
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
