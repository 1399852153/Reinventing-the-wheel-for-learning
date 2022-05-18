package disruptor.v4;

import disruptor.api.MyEventConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class WorkerPoolV4<T> {

    private final SequenceV4 workSequence = new SequenceV4(-1);
    private final MyRingBufferV4<T> myRingBuffer;
    private final List<WorkerEventProcessorV4<T>> workerEventProcessors;

    public WorkerPoolV4(
            final MyRingBufferV4<T> myRingBuffer,
            final SequenceBarrierV4 sequenceBarrier,
            final List<MyEventConsumer<T>> myEventConsumerList) {

        this.myRingBuffer = myRingBuffer;
        final int numWorkers = myEventConsumerList.size();
        workerEventProcessors = new ArrayList<>(numWorkers);

        for (MyEventConsumer<T> myEventConsumer : myEventConsumerList) {
            workerEventProcessors.add(new WorkerEventProcessorV4<>(
                    myRingBuffer,
                    myEventConsumer,
                    sequenceBarrier,
                    this.workSequence));
        }
    }

    public WorkerPoolV4(
            final MyRingBufferV4<T> myRingBuffer,
            final List<MyEventConsumer<T>> myEventConsumerList) {

        this.myRingBuffer = myRingBuffer;
        SequenceBarrierV4 sequenceBarrier = myRingBuffer.getSequenceBarrier();
        final int numWorkers = myEventConsumerList.size();
        workerEventProcessors = new ArrayList<>(numWorkers);

        for (MyEventConsumer<T> myEventConsumer : myEventConsumerList) {
            workerEventProcessors.add(new WorkerEventProcessorV4<>(
                    myRingBuffer,
                    myEventConsumer,
                    sequenceBarrier,
                    this.workSequence));
        }

        // 将workerPool的所有序列加入ringBuffer，用于控制生产者的速度
        myRingBuffer.addConsumerSequence(getCurrentWorkerSequences());
    }

    public MyRingBufferV4<T> start(final Executor executor) {
        final long cursor = myRingBuffer.getCurrentProducerSequence().getRealValue();
        workSequence.setRealValue(cursor);

        for (WorkerEventProcessorV4<?> processor : workerEventProcessors)
        {
            processor.getCurrentConsumeSequence().setRealValue(cursor);
            executor.execute(processor);
        }

        return this.myRingBuffer;
    }

    /**
     * 返回包括每个workerEventProcessor + workerPool自身的序列号集合
     * */
    public SequenceV4[] getCurrentWorkerSequences() {
        final SequenceV4[] sequences = new SequenceV4[workerEventProcessors.size() + 1];
        for (int i = 0, size = workerEventProcessors.size(); i < size; i++) {
            sequences[i] = workerEventProcessors.get(i).getCurrentConsumeSequence();
        }
        sequences[sequences.length - 1] = workSequence;

        return sequences;
    }
}
