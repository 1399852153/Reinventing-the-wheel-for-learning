package blockingqueue.statistic;

import blockingqueue.MyBlockingQueue;
import blockingqueue.array.*;
import blockingqueue.util.BlockingQueueTestUtil;

/**
 * @author xiongyx
 * @date 2021/3/25
 */
public class BlockingQueuePerformanceTest {

    /**
     * 队列容量
     * */
    private static final int QUEUE_CAPACITY = 5;

    /**
     * 并发线程数（消费者 + 生产者 = 2 * WORKER_NUM）
     * */
    private static final int WORKER_NUM = 100;

    /**
     * 单次测试中每个线程访问队列的次数
     * */
    private static final int PER_WORKER_PROCESS_NUM = 1000;

    /**
     * 重复执行的次数
     * */
    private static final int REPEAT_TIME = 20;


    public static void main(String[] args) throws InterruptedException {
//        {
//            MyBlockingQueue<Integer> myArrayBlockingQueueV2 = new MyArrayBlockingQueueV2<>(QUEUE_CAPACITY);
//            long avgCostTimeV2 = BlockingQueueTestUtil.statisticBlockingQueueRuntime(myArrayBlockingQueueV2, WORKER_NUM, PER_WORKER_PROCESS_NUM, REPEAT_TIME);
//            System.out.println(costTimeLog(MyArrayBlockingQueueV2.class, avgCostTimeV2));
//        }

//        {
//            MyBlockingQueue<Integer> myArrayBlockingQueueV3 = new MyArrayBlockingQueueV3<>(QUEUE_CAPACITY);
//            long avgCostTimeV3 = BlockingQueueTestUtil.statisticBlockingQueueRuntime(myArrayBlockingQueueV3, WORKER_NUM, PER_WORKER_PROCESS_NUM, REPEAT_TIME);
//            System.out.println(costTimeLog(MyArrayBlockingQueueV3.class, avgCostTimeV3));
//        }
//
//        {
//            MyBlockingQueue<Integer> myArrayBlockingQueueV4 = new MyArrayBlockingQueueV4<>(QUEUE_CAPACITY);
//            long avgCostTimeV4 = BlockingQueueTestUtil.statisticBlockingQueueRuntime(myArrayBlockingQueueV4, WORKER_NUM, PER_WORKER_PROCESS_NUM, REPEAT_TIME);
//            System.out.println(costTimeLog(MyArrayBlockingQueueV4.class, avgCostTimeV4));
//        }
//
//        {
//            MyBlockingQueue<Integer> myArrayBlockingQueueV5 = new MyArrayBlockingQueueV5<>(QUEUE_CAPACITY);
//            long avgCostTimeV5 = BlockingQueueTestUtil.statisticBlockingQueueRuntime(myArrayBlockingQueueV5, WORKER_NUM, PER_WORKER_PROCESS_NUM, REPEAT_TIME);
//            System.out.println(costTimeLog(MyArrayBlockingQueueV5.class, avgCostTimeV5));
//        }
//
//        {
//            MyBlockingQueue<Integer> jdkArrayBlockingQueue = new JDKArrayBlockingQueue<>(QUEUE_CAPACITY);
//            long avgCostTimeJDK = BlockingQueueTestUtil.statisticBlockingQueueRuntime(jdkArrayBlockingQueue, WORKER_NUM, PER_WORKER_PROCESS_NUM, REPEAT_TIME);
//            System.out.println(costTimeLog(JDKArrayBlockingQueue.class, avgCostTimeJDK));
//        }
//
//        {
//            MyBlockingQueue<Integer> jdkLinkedBlockingQueue = new JDKLinkedBlockingQueue<>(QUEUE_CAPACITY);
//            long avgCostTimeJDK = BlockingQueueTestUtil.statisticBlockingQueueRuntime(jdkLinkedBlockingQueue, WORKER_NUM, PER_WORKER_PROCESS_NUM, REPEAT_TIME);
//            System.out.println(costTimeLog(JDKLinkedBlockingQueue.class, avgCostTimeJDK));
//        }

        {
            MyBlockingQueue<Integer> myArrayBlockingQueueWithMyAQS = new MyArrayBlockingQueueWithMyAQS<>(QUEUE_CAPACITY);
            long avgCostTimeMyAQS = BlockingQueueTestUtil.statisticBlockingQueueRuntime(myArrayBlockingQueueWithMyAQS, WORKER_NUM, PER_WORKER_PROCESS_NUM, REPEAT_TIME);
            System.out.println(costTimeLog(MyArrayBlockingQueueWithMyAQS.class, avgCostTimeMyAQS));
        }
    }

    private static String costTimeLog(Class blockQueueCLass,long costTime){
        return blockQueueCLass.getSimpleName() + " avgCostTime=" + costTime + "ms";
    }
}
