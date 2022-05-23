package disruptor.v5;

import disruptor.api.MyEventConsumer;
import disruptor.api.MyEventProducer;
import disruptor.api.ProducerType;

import java.util.Arrays;
import java.util.concurrent.Executor;

public class MyDisruptor<T> {

    private final MyRingBufferV5<T> ringBuffer;
    private final Executor executor;
    private final MyConsumerRepository<T> consumerRepository = new MyConsumerRepository<>();


    public MyDisruptor(
            final MyEventProducer<T> eventProducer,
            final int ringBufferSize,
            final Executor executor,
            final ProducerType producerType,
            final BlockingWaitStrategyV5 blockingWaitStrategyV5) {

        this.ringBuffer = MyRingBufferV5.create(producerType,eventProducer,ringBufferSize, blockingWaitStrategyV5);
        this.executor = executor;
    }

    /**
     * 单线程消费者
     * */
    public EventHandlerGroup<T> handleEventsWith(final MyEventConsumer<T>... myEventConsumers){
       return createEventProcessors(new SequenceV5[0],myEventConsumers);
    }

    public EventHandlerGroup<T> createEventProcessors(
            final SequenceV5[] barrierSequences,
            final MyEventConsumer<T>[] myEventConsumers) {

        final SequenceV5[] processorSequences = new SequenceV5[myEventConsumers.length];
        final SequenceBarrierV5 barrier = ringBuffer.newBarrier(barrierSequences);

        int i=0;
        for(MyEventConsumer<T> myEventConsumer : myEventConsumers){
            final BatchEventProcessorV5<T> batchEventProcessor =
                    new BatchEventProcessorV5<T>(ringBuffer, myEventConsumer, barrier);

            processorSequences[i] = batchEventProcessor.getCurrentConsumeSequence();
            i++;

            // consumer都保存起来，便于start启动
            consumerRepository.add(batchEventProcessor);
        }

        updateGatingSequencesForNextInChain(barrierSequences,processorSequences);

        return new EventHandlerGroup<>(this,this.consumerRepository,processorSequences);
    }

    public final EventHandlerGroup<T> handleEventsWithWorkerPool(final MyEventConsumer<T>... myEventConsumers) {
        return createWorkerPool(new SequenceV5[0], myEventConsumers);
    }

    public EventHandlerGroup<T> createWorkerPool(
            final SequenceV5[] barrierSequences, final MyEventConsumer<T>[] workHandlers) {
        final SequenceBarrierV5 sequenceBarrier = ringBuffer.newBarrier(barrierSequences);
        final WorkerPoolV5<T> workerPool = new WorkerPoolV5<>(ringBuffer, sequenceBarrier, Arrays.asList(workHandlers));

        // consumer都保存起来，便于start启动
        consumerRepository.add(workerPool);

        final SequenceV5[] workerSequences = workerPool.getCurrentWorkerSequences();

        updateGatingSequencesForNextInChain(barrierSequences, workerSequences);

        return new EventHandlerGroup<T>(this, consumerRepository,workerSequences);
    }

    public MyRingBufferV5<T> getRingBuffer() {
        return ringBuffer;
    }

    public void start(){
        // 遍历所有的消费者，挨个start启动
        this.consumerRepository.getConsumerInfos()
                .forEach(item->item.start(this.executor));
    }

    private void updateGatingSequencesForNextInChain(final SequenceV5[] barrierSequences, final SequenceV5[] processorSequences) {
        if (processorSequences.length != 0) {
            // 由于新的消费者通过ringBuffer.newBarrier(barrierSequences)，已经是依赖于之前ringBuffer中已有的消费者序列
            // 消费者即EventProcessor内部已经设置好了老的barrierSequences为依赖，因此可以将ringBuffer中已有的消费者序列去掉
            // 只需要保存，依赖当前消费者链条最末端的序列即可（也就是最慢的序列）
            for(SequenceV5 sequenceV5 : barrierSequences){
                ringBuffer.removeConsumerSequence(sequenceV5);
            }
            for(SequenceV5 sequenceV5 : processorSequences){
                // 新设置的就是当前消费者链条最末端的序列
                ringBuffer.addConsumerSequence(sequenceV5);
            }
        }
    }
}
