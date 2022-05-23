package disruptor.v5;

import disruptor.api.MyEventConsumer;

public class EventHandlerGroup<T> {

    private final MyDisruptor<T> disruptor;
    private final MyConsumerRepository<T> myConsumerRepository;
    private final SequenceV5[] sequences;


    public EventHandlerGroup(MyDisruptor<T> disruptor,
                             MyConsumerRepository<T> myConsumerRepository,
                             SequenceV5[] sequences) {
        this.disruptor = disruptor;
        this.myConsumerRepository = myConsumerRepository;
        this.sequences = sequences;
    }

    public final EventHandlerGroup<T> then(final MyEventConsumer<T>... myEventConsumers) {
        return handleEventsWith(myEventConsumers);
    }

    public final EventHandlerGroup<T> handleEventsWith(final MyEventConsumer<T>... handlers) {
        return disruptor.createEventProcessors(sequences, handlers);
    }
}

