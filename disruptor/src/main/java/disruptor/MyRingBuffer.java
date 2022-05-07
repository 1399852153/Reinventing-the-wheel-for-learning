package disruptor;


import disruptor.api.MyEventProducer;

/**
 * 仿disruptor 环形队列
 * @author shanreng
 */
public class MyRingBuffer<T> {

    private T[] elementList;
    private final SingleProducerSequencer singleProducerSequencer;
    private final MyEventProducer<T> myEventProducer;
    private final SequenceBarrier<T> sequenceBarrier;
    private final int ringBufferSize;
    private final int mask;

    public MyRingBuffer(SingleProducerSequencer singleProducerSequencer, MyEventProducer<T> myEventProducer) {
        this.singleProducerSequencer = singleProducerSequencer;
        this.myEventProducer = myEventProducer;
        this.sequenceBarrier = new SequenceBarrier<>(this);
        this.ringBufferSize = singleProducerSequencer.getRingBufferSize();
        // 回环掩码
        this.mask = ringBufferSize - 1;

        // 预填充事件对象（后续生产者/消费者都只会更新事件对象，不会发生插入、删除等操作，避免GC）
        fillElementList();
    }

    private void fillElementList(){
        for(int i=0; i<this.elementList.length; i++){
            this.elementList[i] = this.myEventProducer.newInstance();
        }
    }

    public void publish(int index){
        this.singleProducerSequencer.publish(index);
        this.sequenceBarrier.signalAllWhenBlocking();
    }

    public SingleProducerSequencer getSingleProducerSequencer() {
        return singleProducerSequencer;
    }

    public SequenceBarrier<T> getSequenceBarrier(){
        return this.sequenceBarrier;
    }

    public T get(long index){
        return elementList[(int) (index % mask)];
    }
}
