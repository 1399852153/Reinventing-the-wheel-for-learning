package disruptor.v2;

import java.util.List;

/**
 * ringBuffer的序列屏障
 * */
public class SequenceBarrierV2 {

    private final SequenceV2 currentProducerSequence;
    private final BlockingWaitStrategyV2 blockingWaitStrategyV2;
    private final List<SequenceV2> dependentSequencesList;

    public SequenceBarrierV2(SequenceV2 currentProducerSequence, BlockingWaitStrategyV2 blockingWaitStrategyV2,
                             List<SequenceV2> dependentSequencesList) {
        this.currentProducerSequence = currentProducerSequence;
        this.blockingWaitStrategyV2 = blockingWaitStrategyV2;
        this.dependentSequencesList = dependentSequencesList;
    }

    /**
     * 获得可用的最大消费者下标(如果没有)
     * */
    public long getAvailableConsumeSequence(long currentConsumeSequence) throws InterruptedException {
        return blockingWaitStrategyV2.waitFor(currentConsumeSequence, currentProducerSequence,dependentSequencesList);
    }
}
