package disruptor.v4.api;

import disruptor.v4.SequenceBarrierV4;
import disruptor.v4.SequenceV4;

import java.util.List;

public interface ProducerSequencer {

    long next();

    void publish(long publishIndex);

    void addConsumerSequence(SequenceV4 consumerSequenceV4);

    void addConsumerSequenceList(List<SequenceV4> consumerSequenceV4);

    int getRingBufferSize();

    SequenceBarrierV4 newBarrier();

    SequenceBarrierV4 newBarrier(SequenceV4... dependenceSequences);

    SequenceV4 getCurrentMaxProducerSequence();

    boolean isAvailable(long sequence);
}
