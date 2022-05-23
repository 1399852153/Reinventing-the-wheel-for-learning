package disruptor.v5.api;

import disruptor.v5.SequenceBarrierV5;
import disruptor.v5.SequenceV5;

import java.util.List;

public interface ProducerSequencer {

    long next();

    void publish(long publishIndex);

    void addConsumerSequence(SequenceV5 consumerSequenceV5);

    void addConsumerSequenceList(List<SequenceV5> consumerSequenceV5);

    void removeConsumerSequence(SequenceV5 consumerSequenceV5);

    int getRingBufferSize();

    SequenceBarrierV5 newBarrier();

    SequenceBarrierV5 newBarrier(SequenceV5... dependenceSequences);

    SequenceV5 getCurrentMaxProducerSequence();

    boolean isAvailable(long sequence);

    long getHighestPublishedSequence(long nextSequence, long availableSequence);

}
