package disruptor;


/**
 * 仿disruptor 环形队列
 * @author shanreng
 */
public class MyRingBuffer<T> {

    private T[] elementList;
    private SingleProducerSequencer singleProducerSequencer;
    private final int ringBufferSize;
    private final int mask;

    public MyRingBuffer(SingleProducerSequencer singleProducerSequencer) {
        this.singleProducerSequencer = singleProducerSequencer;
        this.ringBufferSize = singleProducerSequencer.getRingBufferSize();
        // 回环掩码
        this.mask = ringBufferSize - 1;
    }

    public SingleProducerSequencer getSingleProducerSequencer() {
        return singleProducerSequencer;
    }

    public T get(long index){
        return elementList[(int) (index % mask)];
    }
}
