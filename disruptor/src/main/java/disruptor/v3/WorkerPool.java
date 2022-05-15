package disruptor.v3;

import disruptor.api.MyEventConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class WorkerPool<T> {

    private final SequenceV3 workSequence = new SequenceV3(-1);
    private final MyRingBufferV3<T> myRingBuffer;
    private final List<WorkerEventProcessor<T>> workerEventProcessors;

    public WorkerPool(
            final MyRingBufferV3<T> myRingBuffer,
            final SequenceBarrierV3 sequenceBarrier,
            final List<MyEventConsumer<T>> myEventConsumerList) {

        this.myRingBuffer = myRingBuffer;
        final int numWorkers = myEventConsumerList.size();
        workerEventProcessors = new ArrayList<>(numWorkers);

        for (MyEventConsumer<T> myEventConsumer : myEventConsumerList) {
            workerEventProcessors.add(new WorkerEventProcessor<>(
                    myRingBuffer,
                    myEventConsumer,
                    sequenceBarrier,
                    this.workSequence));
        }
    }

    public WorkerPool(
            final MyRingBufferV3<T> myRingBuffer,
            final List<MyEventConsumer<T>> myEventConsumerList) {

        this.myRingBuffer = myRingBuffer;
        SequenceBarrierV3 sequenceBarrier = myRingBuffer.getSequenceBarrier();
        final int numWorkers = myEventConsumerList.size();
        workerEventProcessors = new ArrayList<>(numWorkers);

        for (MyEventConsumer<T> myEventConsumer : myEventConsumerList) {
            workerEventProcessors.add(new WorkerEventProcessor<>(
                    myRingBuffer,
                    myEventConsumer,
                    sequenceBarrier,
                    this.workSequence));
        }

        // 将workerPool的所有序列加入ringBuffer，用于控制生产者的速度
        myRingBuffer.addConsumerSequence(getCurrentWorkerSequences());
    }

    public MyRingBufferV3<T> start(final Executor executor) {
        final long cursor = myRingBuffer.getCurrentProducerSequence().getRealValue();
        workSequence.setRealValue(cursor);

        for (WorkerEventProcessor<?> processor : workerEventProcessors)
        {
            processor.getCurrentConsumeSequence().setRealValue(cursor);
            executor.execute(processor);
        }

        return this.myRingBuffer;
    }

    /**
     * 返回包括每个workerEventProcessor + workerPool自身的序列号集合
     * */
    public SequenceV3[] getCurrentWorkerSequences() {
        final SequenceV3[] sequences = new SequenceV3[workerEventProcessors.size() + 1];
        for (int i = 0, size = workerEventProcessors.size(); i < size; i++) {
            sequences[i] = workerEventProcessors.get(i).getCurrentConsumeSequence();
        }
        sequences[sequences.length - 1] = workSequence;

        return sequences;
    }
}
