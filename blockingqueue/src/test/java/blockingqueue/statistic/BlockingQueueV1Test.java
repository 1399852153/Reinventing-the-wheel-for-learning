package blockingqueue.statistic;

import blockingqueue.MyBlockingQueue;
import blockingqueue.util.BlockingQueueTestUtil;
import blockingqueue.v1.MyArrayBlockingQueueV1;

/**
 * @author xiongyx
 * @date 2021/3/24
 */
public class BlockingQueueV1Test {

    public static void main(String[] args) throws InterruptedException {
        MyBlockingQueue<Integer> blockingQueue = new MyArrayBlockingQueueV1<>(2);

        long avgCostTime = BlockingQueueTestUtil.statisticBlockingQueueRuntime(blockingQueue,10,30,5);

        assert blockingQueue.isEmpty();

        System.out.println("avgCostTime=" + avgCostTime);
    }
}
