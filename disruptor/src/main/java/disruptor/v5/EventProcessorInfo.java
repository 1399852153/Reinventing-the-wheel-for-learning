package disruptor.v5;

import disruptor.v5.api.EventProcessorV5;

import java.util.concurrent.Executor;

public class EventProcessorInfo<T> implements ConsumerInfo {

    private final EventProcessorV5 eventProcessor;

    public EventProcessorInfo(EventProcessorV5 eventProcessor) {
        this.eventProcessor = eventProcessor;
    }


    @Override
    public void start(Executor executor) {
        executor.execute(eventProcessor);
    }
}
