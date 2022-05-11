package disruptor.v1;


import disruptor.api.MyEventProducer;

/**
 * 仿disruptor 环形队列
 * @author shanreng
 */
public class MyRingBufferV1<T> {

    private final T[] elementList;
    private final SingleProducerSequencerV1 singleProducerSequencerV1;
    private final MyEventProducer<T> myEventProducer;
    private final SequenceBarrierV1<T> sequenceBarrierV1;
    private final int ringBufferSize;
    private final int mask;

    public MyRingBufferV1(SingleProducerSequencerV1 singleProducerSequencerV1, MyEventProducer<T> myEventProducer) {
        this.singleProducerSequencerV1 = singleProducerSequencerV1;
        this.myEventProducer = myEventProducer;
        this.sequenceBarrierV1 = new SequenceBarrierV1<>(this);
        this.ringBufferSize = singleProducerSequencerV1.getRingBufferSize();
        this.elementList = (T[]) new Object[this.ringBufferSize];
        // 回环掩码
        this.mask = ringBufferSize;

        // 预填充事件对象（后续生产者/消费者都只会更新事件对象，不会发生插入、删除等操作，避免GC）
        fillElementList();
    }

    private void fillElementList(){
        for(int i=0; i<this.elementList.length; i++){
            this.elementList[i] = this.myEventProducer.newInstance();
        }
    }

    public void publish(Long index){
        this.singleProducerSequencerV1.publish(index);
        this.sequenceBarrierV1.signalAllWhenBlocking();
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public void setConsumerSequence(SequenceV1 consumerSequenceV1){
        this.singleProducerSequencerV1.setConsumerSequence(consumerSequenceV1);
    }

    public SingleProducerSequencerV1 getSingleProducerSequencer() {
        return singleProducerSequencerV1;
    }

    public SequenceBarrierV1<T> getSequenceBarrier(){
        return this.sequenceBarrierV1;
    }

    public T get(long sequence){
        int index = (int) (sequence % mask);
        return elementList[index];
    }
}
