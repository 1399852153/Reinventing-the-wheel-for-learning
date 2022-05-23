package disruptor.v5.api;

import disruptor.v5.SequenceV5;

public interface EventProcessorV5 extends Runnable{

    SequenceV5 getCurrentConsumeSequence();
}
