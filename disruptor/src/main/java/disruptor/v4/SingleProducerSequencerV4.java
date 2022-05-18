package disruptor.v4;

import disruptor.util.LogUtil;
import disruptor.v4.util.SequenceUtilV4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * 单生产者序列器
 * */
public class SingleProducerSequencerV4 {

    private final int ringBufferSize;
    private final SequenceV4 currentProducerSequence = new SequenceV4(-1);
    private final List<SequenceV4> gatingConsumerSequence = new ArrayList<>();
    private final BlockingWaitStrategyV4 blockingWaitStrategyV3;

    public SingleProducerSequencerV4(int ringBufferSize, BlockingWaitStrategyV4 blockingWaitStrategyV3) {
        this.ringBufferSize = ringBufferSize;
        this.blockingWaitStrategyV3 = blockingWaitStrategyV3;
    }

    /**
     * 申请下一个序列的权限
     * */
    public long next(){
        // 申请之后的生产者位点
        long nextProducerSequence = this.currentProducerSequence.getRealValue() + 1;

        boolean firstWaiting = true;

        // 申请之后的生产者位点是否超过了最慢的消费者位点一圈
        while(nextProducerSequence > SequenceUtilV4.getMinimumSequence(nextProducerSequence,this.gatingConsumerSequence) + (this.ringBufferSize)){
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
        this.blockingWaitStrategyV3.signalAllWhenBlocking();
    }

    public void addConsumerSequence(SequenceV4 consumerSequenceV3){
        this.gatingConsumerSequence.add(consumerSequenceV3);
    }

    public void addConsumerSequenceList(List<SequenceV4> consumerSequenceV3){
        this.gatingConsumerSequence.addAll(consumerSequenceV3);
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public SequenceBarrierV4 newBarrier(){
        return new SequenceBarrierV4(this.currentProducerSequence,this.blockingWaitStrategyV3,new ArrayList<>());
    }

    /**
     * 有依赖关系的栅栏（返回的barrier依赖于传入的barrier集合中最小的序列）
     * */
    public SequenceBarrierV4 newBarrier(SequenceV4... dependenceSequences){
        return new SequenceBarrierV4(this.currentProducerSequence,this.blockingWaitStrategyV3,new ArrayList<>(Arrays.asList(dependenceSequences)));
    }

    public SequenceV4 getCurrentProducerSequence() {
        return currentProducerSequence;
    }
}
