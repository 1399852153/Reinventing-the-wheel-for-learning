package disruptor.v4;


import disruptor.api.MyEventProducer;

import java.util.Arrays;

/**
 * 仿disruptor 环形队列
 * @author shanreng
 */
public class MyRingBufferV4<T> {

    private final T[] elementList;
    private final SingleProducerSequencerV4 singleProducerSequencer;
    private final MyEventProducer<T> myEventProducer;
    private final int ringBufferSize;
    private final int mask;
    private final BlockingWaitStrategyV4 blockingWaitStrategyV4 = new BlockingWaitStrategyV4();

    public MyRingBufferV4(int ringBufferSize, MyEventProducer<T> myEventProducer) {
        this.singleProducerSequencer = new SingleProducerSequencerV4(ringBufferSize,this.blockingWaitStrategyV4);
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

    public SequenceBarrierV4 newBarrier() {
        return this.singleProducerSequencer.newBarrier();
    }

    public SequenceBarrierV4 newBarrier(SequenceV4... dependenceSequences) {
        return this.singleProducerSequencer.newBarrier(dependenceSequences);
    }

    public void addConsumerSequence(SequenceV4 consumerSequenceV4){
        this.singleProducerSequencer.addConsumerSequence(consumerSequenceV4);
    }

    public void addConsumerSequence(SequenceV4... gatingSequences)
    {
        this.singleProducerSequencer.addConsumerSequenceList(Arrays.asList(gatingSequences));
    }

    public SequenceBarrierV4 getSequenceBarrier(){
        return this.singleProducerSequencer.newBarrier();
    }

    public SequenceV4 getCurrentProducerSequence(){
        return this.singleProducerSequencer.getCurrentMaxProducerSequence();
    }

    public T get(long sequence){
        int index = (int) (sequence % mask);
        return elementList[index];
    }
}
