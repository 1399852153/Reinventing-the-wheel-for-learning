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

    /**
     * 解决伪共享 左半部分填充
     * */
    private long lp1, lp2, lp3, lp4, lp5, lp6, lp7;

    /**
     * 访问最频繁的两个变量，避免伪共享
     * */
    private long nextValue = SequenceV5.INITIAL_VALUE;
    private long cachedValue = SequenceV5.INITIAL_VALUE;

    /**
     * 解决伪共享 右半部分填充
     * */
    private long rp1, rp2, rp3, rp4, rp5, rp6, rp7;

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
        long nextProducerSequence = this.nextValue + 1;
        // 是否生产者超过消费者一圈的环绕临界点序列
        long wrapPoint = nextProducerSequence - this.ringBufferSize;

        // 获得已缓存的最小消费者序列
        long cachedGatingSequence = this.cachedValue;

        boolean firstWaiting = true;

        // 最小消费者序列cachedValue并不是实时获取的（因为在没有超过环绕点一圈时，是可以放心生产的）
        // 实时获取反而会发起对消费者sequence强一致的读，逼迫消费者线程刷缓存（而这是不需要的）
        if(wrapPoint > cachedGatingSequence){
            long minSequence;

            // 当生产者发现确实当前已经超过了一圈，则必须去读最新的消费者序列了
            while(wrapPoint > (minSequence = SequenceUtilV5.getMinimumSequence(nextProducerSequence,this.gatingConsumerSequence))){
                if(firstWaiting){
                    firstWaiting = false;
                    LogUtil.logWithThreadName("生产者陷入阻塞");
                }
                // 如果确实超过了一圈，则生产者无法获取队列空间，无限循环的park超时阻塞
                LockSupport.parkNanos(1L);
            }

            // 满足条件了，则缓存最新的最小消费者序列
            // 因为不是实时获取消费者的最小序列，可能cachedValue比之前的要前进很多
            this.cachedValue = minSequence;
        }

        this.nextValue = nextProducerSequence;

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
