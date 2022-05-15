package disruptor.v3;


import disruptor.api.MyEventProducer;

import java.util.Arrays;

/**
 * 仿disruptor 环形队列
 * @author shanreng
 */
public class MyRingBufferV3<T> {

    private final T[] elementList;
    private final SingleProducerSequencerV3 singleProducerSequencer;
    private final MyEventProducer<T> myEventProducer;
    private final int ringBufferSize;
    private final int mask;
    private final BlockingWaitStrategyV3 blockingWaitStrategyV3 = new BlockingWaitStrategyV3();

    public MyRingBufferV3(int ringBufferSize, MyEventProducer<T> myEventProducer) {
        this.singleProducerSequencer = new SingleProducerSequencerV3(ringBufferSize,this.blockingWaitStrategyV3);
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

    public SequenceBarrierV3 newBarrier() {
        return this.singleProducerSequencer.newBarrier();
    }

    public SequenceBarrierV3 newBarrier(SequenceV3... dependenceSequences) {
        return this.singleProducerSequencer.newBarrier(dependenceSequences);
    }

    public void addConsumerSequence(SequenceV3 consumerSequenceV3){
        this.singleProducerSequencer.addConsumerSequence(consumerSequenceV3);
    }

    public void addConsumerSequence(SequenceV3... gatingSequences)
    {
        this.singleProducerSequencer.addConsumerSequenceList(Arrays.asList(gatingSequences));
    }

    public SequenceBarrierV3 getSequenceBarrier(){
        return this.singleProducerSequencer.newBarrier();
    }

    public SequenceV3 getCurrentProducerSequence(){
        return this.singleProducerSequencer.getCurrentProducerSequence();
    }

    public T get(long sequence){
        int index = (int) (sequence % mask);
        return elementList[index];
    }
}
