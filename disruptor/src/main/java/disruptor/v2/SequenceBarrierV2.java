package disruptor.v2;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ringBuffer的序列屏障
 * */
public class SequenceBarrierV2 {

    private final SequenceV2 currentSequence;
    private final BlockingWaitStrategy blockingWaitStrategy = new BlockingWaitStrategy();
    private final List<SequenceV2> dependentSequencesList;

    public SequenceBarrierV2(SequenceV2 currentSequence,List<SequenceV2> dependentSequencesList) {
        this.currentSequence = currentSequence;
        this.dependentSequencesList = dependentSequencesList;
    }

    /**
     * 获得可用的最大消费者下标(如果没有)
     * */
    public long getAvailableConsumeSequence(long currentConsumeSequence) throws InterruptedException {
        return blockingWaitStrategy.waitFor(currentConsumeSequence,currentSequence,dependentSequencesList);
    }
}
