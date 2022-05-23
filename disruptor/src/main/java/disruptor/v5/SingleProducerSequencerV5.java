package disruptor.v5;

import disruptor.util.LogUtil;
import disruptor.v5.api.ProducerSequencer;
import disruptor.v5.util.SequenceUtilV5;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * 单生产者序列器
 * */
public class SingleProducerSequencerV5 implements ProducerSequencer {

    private final int ringBufferSize;
    private final SequenceV5 currentProducerSequence = new SequenceV5(-1);
    private final List<SequenceV5> gatingConsumerSequence = new ArrayList<>();
    private final BlockingWaitStrategyV5 blockingWaitStrategyV5;

    public SingleProducerSequencerV5(int ringBufferSize, BlockingWaitStrategyV5 blockingWaitStrategyV5) {
        this.ringBufferSize = ringBufferSize;
        this.blockingWaitStrategyV5 = blockingWaitStrategyV5;
    }

    /**
     * 申请下一个序列的权限
     * */
    @Override
    public long next(){
        // 申请之后的生产者位点
        long nextProducerSequence = this.currentProducerSequence.getRealValue() + 1;

        boolean firstWaiting = true;

        // 申请之后的生产者位点是否超过了最慢的消费者位点一圈
        while(nextProducerSequence > SequenceUtilV5.getMinimumSequence(nextProducerSequence,this.gatingConsumerSequence) + (this.ringBufferSize)){
            if(firstWaiting){
                firstWaiting = false;
                LogUtil.logWithThreadName("生产者陷入阻塞");
            }
            // 如果确实超过了一圈，则生产者无法获取队列空间，无限循环的park超时阻塞
            LockSupport.parkNanos(1L);
        }

        return nextProducerSequence;
    }

    @Override
    public void publish(long publishIndex){
        this.currentProducerSequence.setRealValue(publishIndex);
        this.blockingWaitStrategyV5.signalAllWhenBlocking();
    }

    @Override
    public void addConsumerSequence(SequenceV5 consumerSequenceV5){
        this.gatingConsumerSequence.add(consumerSequenceV5);
    }

    @Override
    public void addConsumerSequenceList(List<SequenceV5> consumerSequenceV5){
        this.gatingConsumerSequence.addAll(consumerSequenceV5);
    }

    @Override
    public void removeConsumerSequence(SequenceV5 consumerSequenceV5) {
        this.gatingConsumerSequence.remove(consumerSequenceV5);
    }

    @Override
    public int getRingBufferSize() {
        return ringBufferSize;
    }

    @Override
    public SequenceBarrierV5 newBarrier(){
        return new SequenceBarrierV5(this.currentProducerSequence,this.blockingWaitStrategyV5,new ArrayList<>());
    }

    /**
     * 有依赖关系的栅栏（返回的barrier依赖于传入的barrier集合中最小的序列）
     * */
    @Override
    public SequenceBarrierV5 newBarrier(SequenceV5... dependenceSequences){
        return new SequenceBarrierV5(this.currentProducerSequence,this.blockingWaitStrategyV5,new ArrayList<>(Arrays.asList(dependenceSequences)));
    }

    @Override
    public SequenceV5 getCurrentMaxProducerSequence() {
        return currentProducerSequence;
    }

    @Override
    public boolean isAvailable(long sequence) {
        return sequence <= this.currentProducerSequence.getRealValue();
    }

    @Override
    public long getHighestPublishedSequence(long nextSequence, long availableSequence) {
        // 该方法主要用于多生产者时，修正已发布的最高生产者序列号
        // 多生产者时，其cursor不再代表已发布的最小序列号
        // 因为可能还存在某个生产者线程next申请了一个序列，但还未publish过，此时别的生产者确publish了更大的序列，修改了cursor（增加了）
        // 单生产者时，最大可消费序列就是availableSequence，仅仅作为一个兼容使用
        return availableSequence;
    }
}
