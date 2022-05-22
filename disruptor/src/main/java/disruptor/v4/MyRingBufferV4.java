package disruptor.v4;


import disruptor.api.MyEventProducer;
import disruptor.api.ProducerType;
import disruptor.v3.BlockingWaitStrategyV3;
import disruptor.v4.api.ProducerSequencer;

import java.util.Arrays;

/**
 * 仿disruptor 环形队列
 * @author shanreng
 */
public class MyRingBufferV4<T> {

    private final T[] elementList;
    private final ProducerSequencer producerSequencer;
    private final MyEventProducer<T> myEventProducer;
    private final int ringBufferSize;
    private final int mask;

    public MyRingBufferV4(ProducerSequencer producerSequencer , MyEventProducer<T> myEventProducer) {
        this.producerSequencer = producerSequencer;
        this.myEventProducer = myEventProducer;
        this.ringBufferSize = this.producerSequencer.getRingBufferSize();
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
        this.producerSequencer.publish(index);
    }

    public long next() {
        return this.producerSequencer.next();
    }

    public SequenceBarrierV4 newBarrier() {
        return this.producerSequencer.newBarrier();
    }

    public SequenceBarrierV4 newBarrier(SequenceV4... dependenceSequences) {
        return this.producerSequencer.newBarrier(dependenceSequences);
    }

    public void addConsumerSequence(SequenceV4 consumerSequenceV4){
        this.producerSequencer.addConsumerSequence(consumerSequenceV4);
    }

    public void addConsumerSequence(SequenceV4... gatingSequences) {
        this.producerSequencer.addConsumerSequenceList(Arrays.asList(gatingSequences));
    }

    public void removeConsumerSequence(SequenceV4 consumerSequenceV4){
        this.producerSequencer.addConsumerSequence(consumerSequenceV4);
    }

    public SequenceBarrierV4 getSequenceBarrier(){
        return this.producerSequencer.newBarrier();
    }

    public SequenceV4 getCurrentProducerSequence(){
        return this.producerSequencer.getCurrentMaxProducerSequence();
    }

    public T get(long sequence){
        int index = (int) (sequence % mask);
        return elementList[index];
    }

    public static <T> MyRingBufferV4<T> create(
            ProducerType producerType,
            MyEventProducer<T> eventProducer,
            int bufferSize,
            BlockingWaitStrategyV4 waitStrategyV4)
    {
        switch (producerType)
        {
            case SINGLE: {
                SingleProducerSequencerV4 singleProducerSequencerV4 = new SingleProducerSequencerV4(bufferSize, waitStrategyV4);
                return new MyRingBufferV4<>(singleProducerSequencerV4, eventProducer);
            }
            case MULTI: {
                MultiProducerSequencer multiProducerSequencer = new MultiProducerSequencer(bufferSize, waitStrategyV4);
                return new MyRingBufferV4<>(multiProducerSequencer, eventProducer);
            }
            default:
                throw new RuntimeException("un support producerType:" + producerType.toString());
        }
    }
}
