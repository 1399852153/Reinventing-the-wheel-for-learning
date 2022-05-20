package disruptor.v4;

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
}

