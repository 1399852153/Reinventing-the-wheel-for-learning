package blockingqueue.v1;

import blockingqueue.MyBlockingQueue;
import blockingqueue.util.BlockingQueueTestUtil;

/**
 * @author xiongyx
 * @date 2021/3/24
 */
public class BlockingQueueV1Test {

    public static void main(String[] args) throws InterruptedException {
        MyBlockingQueue<Integer> blockingQueue = new MyArrayBlockingQueueV1<Integer>(2);

        BlockingQueueTestUtil.statisticBlockingQueueRuntime(blockingQueue,10,30,5);
    }
}
