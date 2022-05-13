package disruptor.v3;

import java.util.List;

/**
 * ringBuffer的序列屏障
 * */
public class SequenceBarrierV3 {

    private final SequenceV3 currentProducerSequence;
    private final BlockingWaitStrategyV3 blockingWaitStrategyV3;
    private final List<SequenceV3> dependentSequencesList;

    public SequenceBarrierV3(SequenceV3 currentProducerSequence, BlockingWaitStrategyV3 blockingWaitStrategyV3,
                             List<SequenceV3> dependentSequencesList) {
        this.currentProducerSequence = currentProducerSequence;
        this.blockingWaitStrategyV3 = blockingWaitStrategyV3;
        this.dependentSequencesList = dependentSequencesList;
    }

    /**
     * 获得可用的最大消费者下标(如果没有)
     * */
    public long getAvailableConsumeSequence(long currentConsumeSequence) throws InterruptedException {
        return blockingWaitStrategyV3.waitFor(currentConsumeSequence, currentProducerSequence,dependentSequencesList);
    }
}
