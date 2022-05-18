package disruptor.v4;

import java.util.List;

/**
 * ringBuffer的序列屏障
 * */
public class SequenceBarrierV4 {

    private final SequenceV4 currentProducerSequence;
    private final BlockingWaitStrategyV4 blockingWaitStrategyV4;
    private final List<SequenceV4> dependentSequencesList;

    public SequenceBarrierV4(SequenceV4 currentProducerSequence, BlockingWaitStrategyV4 blockingWaitStrategyV4,
                             List<SequenceV4> dependentSequencesList) {
        this.currentProducerSequence = currentProducerSequence;
        this.blockingWaitStrategyV4 = blockingWaitStrategyV4;
        this.dependentSequencesList = dependentSequencesList;
    }

    /**
     * 获得可用的最大消费者下标(如果没有)
     * */
    public long getAvailableConsumeSequence(long currentConsumeSequence) throws InterruptedException {
        return blockingWaitStrategyV4.waitFor(currentConsumeSequence, currentProducerSequence,dependentSequencesList);
    }
}
