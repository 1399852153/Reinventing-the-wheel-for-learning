package disruptor.v4;

import disruptor.api.MyEventConsumer;

import java.util.ArrayList;
import java.util.List;

public class EventHandlerGroup<T> {

    private final MyDisruptor<T> disruptor;
    private final MyConsumerRepository<T> myConsumerRepository;
    private final List<SequenceV4> sequences;


    public EventHandlerGroup(MyDisruptor<T> disruptor,
                             MyConsumerRepository<T> myConsumerRepository,
                             List<SequenceV4> sequences) {
        this.disruptor = disruptor;
        this.myConsumerRepository = myConsumerRepository;
        this.sequences = new ArrayList<>(sequences);
    }

    public final EventHandlerGroup<T> then(final MyEventConsumer<T>... myEventConsumers) {
        return handleEventsWith(myEventConsumers);
    }

    public final EventHandlerGroup<T> handleEventsWith(final MyEventConsumer<T>... handlers) {
        return disruptor.createEventProcessors((SequenceV4[]) sequences.toArray(), handlers);
    }
}

