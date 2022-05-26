package disruptor.v5;

import disruptor.v5.api.ProducerSequencer;

import java.util.List;

/**
 * ringBuffer的序列屏障
 * */
public class SequenceBarrierV5 {

    private final ProducerSequencer producerSequencer;
    private final SequenceV5 currentProducerSequence;
    private final BlockingWaitStrategyV5 blockingWaitStrategyV5;
    private final List<SequenceV5> dependentSequencesList;

    public SequenceBarrierV5(ProducerSequencer producerSequencer, SequenceV5 currentProducerSequence,
                             BlockingWaitStrategyV5 blockingWaitStrategyV5, List<SequenceV5> dependentSequencesList) {
        this.producerSequencer = producerSequencer;
        this.currentProducerSequence = currentProducerSequence;
        this.blockingWaitStrategyV5 = blockingWaitStrategyV5;
        this.dependentSequencesList = dependentSequencesList;
    }

    /**
     * 获得可用的最大消费者下标(如果没有)
     * */
    public long getAvailableConsumeSequence(long currentConsumeSequence) throws InterruptedException {

        long availableSequence = blockingWaitStrategyV5.waitFor(currentConsumeSequence, currentProducerSequence,dependentSequencesList);

        if (availableSequence < currentConsumeSequence) {
            return availableSequence;
        }

        return producerSequencer.getHighestPublishedSequence(currentConsumeSequence,availableSequence);
    }
}
