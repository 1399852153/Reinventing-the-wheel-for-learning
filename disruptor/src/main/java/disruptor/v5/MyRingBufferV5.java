package disruptor.v5;


import disruptor.api.MyEventProducer;
import disruptor.api.ProducerType;
import disruptor.v5.api.ProducerSequencer;

import java.util.Arrays;

/**
 * 仿disruptor 环形队列
 * @author shanreng
 */
public class MyRingBufferV5<T> {

    /**
     * 解决伪共享 左半部分填充
     * */
    protected long lp1, lp2, lp3, lp4, lp5, lp6, lp7;

    private final T[] elementList;
    private final ProducerSequencer producerSequencer;
    private final MyEventProducer<T> myEventProducer;
    private final int ringBufferSize;
    private final int mask;

    /**
     * 解决伪共享 右半部分填充
     * */
    protected long rp1, rp2, rp3, rp4, rp5, rp6, rp7;


    public MyRingBufferV5(ProducerSequencer producerSequencer , MyEventProducer<T> myEventProducer) {
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

    public SequenceBarrierV5 newBarrier() {
        return this.producerSequencer.newBarrier();
    }

    public SequenceBarrierV5 newBarrier(SequenceV5... dependenceSequences) {
        return this.producerSequencer.newBarrier(dependenceSequences);
    }

    public void addConsumerSequence(SequenceV5 consumerSequenceV5){
        this.producerSequencer.addConsumerSequence(consumerSequenceV5);
    }

    public void addConsumerSequence(SequenceV5... gatingSequences) {
        this.producerSequencer.addConsumerSequenceList(Arrays.asList(gatingSequences));
    }

    public void removeConsumerSequence(SequenceV5 consumerSequenceV5){
        this.producerSequencer.addConsumerSequence(consumerSequenceV5);
    }

    public SequenceBarrierV5 getSequenceBarrier(){
        return this.producerSequencer.newBarrier();
    }

    public SequenceV5 getCurrentProducerSequence(){
        return this.producerSequencer.getCurrentMaxProducerSequence();
    }

    public T get(long sequence){
        int index = (int) (sequence % mask);
        return elementList[index];
    }

    public static <T> MyRingBufferV5<T> create(
            ProducerType producerType,
            MyEventProducer<T> eventProducer,
            int bufferSize,
            BlockingWaitStrategyV5 waitStrategyV4)
    {
        switch (producerType)
        {
            case SINGLE: {
                SingleProducerSequencerV5 singleProducerSequencerV5 = new SingleProducerSequencerV5(bufferSize, waitStrategyV4);
                return new MyRingBufferV5<>(singleProducerSequencerV5, eventProducer);
            }
            case MULTI: {
                MultiProducerSequencer multiProducerSequencer = new MultiProducerSequencer(bufferSize, waitStrategyV4);
                return new MyRingBufferV5<>(multiProducerSequencer, eventProducer);
            }
            default:
                throw new RuntimeException("un support producerType:" + producerType.toString());
        }
    }
}
