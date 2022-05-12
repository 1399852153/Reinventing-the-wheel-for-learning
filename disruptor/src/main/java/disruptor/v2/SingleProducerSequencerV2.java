package disruptor.v2;

import disruptor.util.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * 单生产者序列器
 * */
public class SingleProducerSequencerV2 {

    private final int ringBufferSize;
    private final SequenceV2 currentProducerSequence = new SequenceV2(-1);
    private final List<SequenceV2> gatingConsumerSequence = new ArrayList<>();
    private final BlockingWaitStrategy blockingWaitStrategy;

    public SingleProducerSequencerV2(int ringBufferSize,BlockingWaitStrategy blockingWaitStrategy) {
        this.ringBufferSize = ringBufferSize;
        this.blockingWaitStrategy = blockingWaitStrategy;
    }

    /**
     * 申请下一个序列的权限
     * */
    public long next(){
        // 申请之后的生产者位点
        long nextProducerSequence = this.currentProducerSequence.getRealValue() + 1;

        boolean firstWaiting = true;
        // 申请之后的生产者位点是否超过了最慢的消费者位点一圈
        while(nextProducerSequence > this.getMinimumSequence(nextProducerSequence) + (this.ringBufferSize-1)){
            if(firstWaiting){
                firstWaiting = false;
                LogUtil.logWithThreadName("生产者陷入阻塞");
            }
            // 如果确实超过了一圈，则生产者无法获取队列空间，无限循环的park超时阻塞
            LockSupport.parkNanos(1L);
        }

        return nextProducerSequence;
    }

    public void publish(long publishIndex){
        this.currentProducerSequence.setRealValue(publishIndex);
        this.blockingWaitStrategy.signalAllWhenBlocking();
    }

    public void addConsumerSequence(SequenceV2 consumerSequenceV2){
        this.gatingConsumerSequence.add(consumerSequenceV2);
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public SequenceBarrierV2 newBarrier(){
        return new SequenceBarrierV2(this.currentProducerSequence,new ArrayList<>());
    }

    /**
     * 有依赖关系的栅栏（返回的barrier依赖于传入的barrier集合中最小的序列）
     * */
    public SequenceBarrierV2 newBarrier(SequenceV2... dependenceSequences){
        return new SequenceBarrierV2(this.currentProducerSequence, new ArrayList<>(Arrays.asList(dependenceSequences)));
    }

    /**
     * 获得gatingSequenceList全部sequence与minimum中最小的序列值
     * */
    public long getMinimumSequence(long minimumSequence) {
        for (SequenceV2 sequence : this.gatingConsumerSequence) {
            long value = sequence.getRealValue();
            minimumSequence = Math.min(minimumSequence, value);
        }

        System.out.println("getMinimumSequence=" + minimumSequence);
        return minimumSequence;
    }
}
