package disruptor.v3.api;

import disruptor.v3.SequenceV3;

public interface EventProcessor {

    SequenceV3 getCurrentConsumeSequence();
}
