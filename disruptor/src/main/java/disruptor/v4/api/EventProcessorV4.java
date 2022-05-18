package disruptor.v4.api;

import disruptor.v4.SequenceV4;

public interface EventProcessorV4 {

    SequenceV4 getCurrentConsumeSequence();
}
