package disruptor.v3;

import java.util.List;

/**
 * ringBuffer的序列屏障
 * */
public class SequenceBarrierV2 {

    private final SequenceV2 currentProducerSequence;
    private final BlockingWaitStrategy blockingWaitStrategy;
    private final List<SequenceV2> dependentSequencesList;

    public SequenceBarrierV2(SequenceV2 currentProducerSequence, BlockingWaitStrategy blockingWaitStrategy,
                             List<SequenceV2> dependentSequencesList) {
        this.currentProducerSequence = currentProducerSequence;
        this.blockingWaitStrategy = blockingWaitStrategy;
        this.dependentSequencesList = dependentSequencesList;
    }

    /**
     * 获得可用的最大消费者下标(如果没有)
     * */
    public long getAvailableConsumeSequence(long currentConsumeSequence) throws InterruptedException {
        return blockingWaitStrategy.waitFor(currentConsumeSequence, currentProducerSequence,dependentSequencesList);
    }
}
