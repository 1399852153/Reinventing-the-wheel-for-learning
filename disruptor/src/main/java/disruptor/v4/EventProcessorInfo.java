package disruptor.v4;

import disruptor.v4.api.EventProcessorV4;

import java.util.concurrent.Executor;

public class EventProcessorInfo<T> implements ConsumerInfo{

    private final EventProcessorV4 eventProcessor;

    public EventProcessorInfo(EventProcessorV4 eventProcessor) {
        this.eventProcessor = eventProcessor;
    }


    @Override
    public void start(Executor executor) {
        executor.execute(eventProcessor);
    }
}
