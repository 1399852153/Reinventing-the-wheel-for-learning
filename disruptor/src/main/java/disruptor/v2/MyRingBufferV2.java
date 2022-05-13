package disruptor.v2;


import disruptor.api.MyEventProducer;

/**
 * 仿disruptor 环形队列
 * @author shanreng
 */
public class MyRingBufferV2<T> {

    private final T[] elementList;
    private final SingleProducerSequencerV2 singleProducerSequencer;
    private final MyEventProducer<T> myEventProducer;
    private final int ringBufferSize;
    private final int mask;
    private final BlockingWaitStrategyV2 blockingWaitStrategyV2 = new BlockingWaitStrategyV2();

    public MyRingBufferV2(int ringBufferSize, MyEventProducer<T> myEventProducer) {
        this.singleProducerSequencer = new SingleProducerSequencerV2(ringBufferSize,this.blockingWaitStrategyV2);
        this.myEventProducer = myEventProducer;
        this.ringBufferSize = singleProducerSequencer.getRingBufferSize();
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
        this.singleProducerSequencer.publish(index);
    }

    public long next() {
        return this.singleProducerSequencer.next();
    }

    public SequenceBarrierV2 newBarrier() {
        return this.singleProducerSequencer.newBarrier();
    }

    public SequenceBarrierV2 newBarrier(SequenceV2... dependenceSequences) {
        return this.singleProducerSequencer.newBarrier(dependenceSequences);
    }

    public void addConsumerSequence(SequenceV2 consumerSequenceV2){
        this.singleProducerSequencer.addConsumerSequence(consumerSequenceV2);
    }

    public SequenceBarrierV2 getSequenceBarrier(){
        return this.singleProducerSequencer.newBarrier();
    }

    public T get(long sequence){
        int index = (int) (sequence % mask);
        return elementList[index];
    }
}
