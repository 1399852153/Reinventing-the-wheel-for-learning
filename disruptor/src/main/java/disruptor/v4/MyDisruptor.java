package disruptor.v4;

import disruptor.api.MyEventConsumer;
import disruptor.api.MyEventProducer;
import disruptor.api.ProducerType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MyDisruptor<T> {

    private final MyRingBufferV4<T> ringBuffer;
    private final Executor executor;
    private final MyConsumerRepository<T> consumerRepository = new MyConsumerRepository<>();


    public MyDisruptor(
            final MyEventProducer<T> eventProducer,
            final int ringBufferSize,
            final Executor executor,
            final ProducerType producerType,
            final BlockingWaitStrategyV4 blockingWaitStrategyV4) {

        this.ringBuffer = MyRingBufferV4.create(producerType,eventProducer,ringBufferSize,blockingWaitStrategyV4);
        this.executor = executor;
    }

    public EventHandlerGroup<T> handleEventsWith(final MyEventConsumer<T>... myEventConsumers){
       return createEventProcessors(new SequenceV4[0],myEventConsumers);
    }

    private EventHandlerGroup<T> createEventProcessors(
            final SequenceV4[] barrierSequences,
            final MyEventConsumer<T>[] myEventConsumers) {

        final List<SequenceV4> processorSequences = new ArrayList<>(myEventConsumers.length);
        final SequenceBarrierV4 barrier = ringBuffer.newBarrier(barrierSequences);

        for(MyEventConsumer<T> myEventConsumer : myEventConsumers){
            final BatchEventProcessorV4<T> batchEventProcessor =
                    new BatchEventProcessorV4<T>(ringBuffer, myEventConsumer, barrier);

            processorSequences.add(batchEventProcessor.getCurrentConsumeSequence());
        }

        // 由于新的消费者通过ringBuffer.newBarrier(barrierSequences)，已经是依赖于之前ringBuffer中已有的消费者序列
        // 消费者即EventProcessor内部已经设置好了老的barrierSequences为依赖，因此可以将ringBuffer中已有的消费者序列去掉
        // 只需要保存，依赖当前消费者链条最末端的序列即可（也就是最慢的序列）
        for(SequenceV4 sequenceV4 : barrierSequences){
            ringBuffer.removeConsumerSequence(sequenceV4);
        }
        for(SequenceV4 sequenceV4 : processorSequences){
            // 新设置的就是当前消费者链条最末端的序列
            ringBuffer.addConsumerSequence(sequenceV4);
        }

        return new EventHandlerGroup<>(this,this.consumerRepository,processorSequences);
    }
}
