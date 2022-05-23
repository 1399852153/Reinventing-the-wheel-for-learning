package disruptor.v5;

import java.util.List;

/**
 * ringBuffer的序列屏障
 * */
public class SequenceBarrierV5 {

    private final SequenceV5 currentProducerSequence;
    private final BlockingWaitStrategyV5 blockingWaitStrategyV5;
    private final List<SequenceV5> dependentSequencesList;

    public SequenceBarrierV5(SequenceV5 currentProducerSequence, BlockingWaitStrategyV5 blockingWaitStrategyV5,
                             List<SequenceV5> dependentSequencesList) {
        this.currentProducerSequence = currentProducerSequence;
        this.blockingWaitStrategyV5 = blockingWaitStrategyV5;
        this.dependentSequencesList = dependentSequencesList;
    }

    /**
     * 获得可用的最大消费者下标(如果没有)
     * */
    public long getAvailableConsumeSequence(long currentConsumeSequence) throws InterruptedException {
        return blockingWaitStrategyV5.waitFor(currentConsumeSequence, currentProducerSequence,dependentSequencesList);
    }
}
