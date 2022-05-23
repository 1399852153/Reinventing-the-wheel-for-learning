package disruptor.v5;

import disruptor.api.MyEventConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class WorkerPoolV5<T> {

    private final SequenceV5 workSequence = new SequenceV5(-1);
    private final MyRingBufferV5<T> myRingBuffer;
    private final List<WorkerEventProcessorV5<T>> workerEventProcessors;

    public WorkerPoolV5(
            final MyRingBufferV5<T> myRingBuffer,
            final SequenceBarrierV5 sequenceBarrier,
            final List<MyEventConsumer<T>> myEventConsumerList) {

        this.myRingBuffer = myRingBuffer;
        final int numWorkers = myEventConsumerList.size();
        workerEventProcessors = new ArrayList<>(numWorkers);

        for (MyEventConsumer<T> myEventConsumer : myEventConsumerList) {
            workerEventProcessors.add(new WorkerEventProcessorV5<>(
                    myRingBuffer,
                    myEventConsumer,
                    sequenceBarrier,
                    this.workSequence));
        }
    }

    public WorkerPoolV5(
            final MyRingBufferV5<T> myRingBuffer,
            final List<MyEventConsumer<T>> myEventConsumerList) {

        this.myRingBuffer = myRingBuffer;
        SequenceBarrierV5 sequenceBarrier = myRingBuffer.getSequenceBarrier();
        final int numWorkers = myEventConsumerList.size();
        workerEventProcessors = new ArrayList<>(numWorkers);

        for (MyEventConsumer<T> myEventConsumer : myEventConsumerList) {
            workerEventProcessors.add(new WorkerEventProcessorV5<>(
                    myRingBuffer,
                    myEventConsumer,
                    sequenceBarrier,
                    this.workSequence));
        }

        // 将workerPool的所有序列加入ringBuffer，用于控制生产者的速度
        myRingBuffer.addConsumerSequence(getCurrentWorkerSequences());
    }

    public MyRingBufferV5<T> start(final Executor executor) {
        final long cursor = myRingBuffer.getCurrentProducerSequence().getRealValue();
        workSequence.setRealValue(cursor);

        for (WorkerEventProcessorV5<?> processor : workerEventProcessors)
        {
            processor.getCurrentConsumeSequence().setRealValue(cursor);
            executor.execute(processor);
        }

        return this.myRingBuffer;
    }

    /**
     * 返回包括每个workerEventProcessor + workerPool自身的序列号集合
     * */
    public SequenceV5[] getCurrentWorkerSequences() {
        final SequenceV5[] sequences = new SequenceV5[workerEventProcessors.size() + 1];
        for (int i = 0, size = workerEventProcessors.size(); i < size; i++) {
            sequences[i] = workerEventProcessors.get(i).getCurrentConsumeSequence();
        }
        sequences[sequences.length - 1] = workSequence;

        return sequences;
    }
}
