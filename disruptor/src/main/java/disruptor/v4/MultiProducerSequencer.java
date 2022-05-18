package disruptor.v4;

import disruptor.v4.api.ProducerSequencer;
import disruptor.v4.util.SequenceUtilV4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

public class MultiProducerSequencer implements ProducerSequencer {

    private final int ringBufferSize;
    private final SequenceV4 currentMaxProducerSequence = new SequenceV4(-1);
    private final List<SequenceV4> gatingConsumerSequence = new ArrayList<>();
    private final BlockingWaitStrategyV4 blockingWaitStrategyV4;

    private final int[] availableBuffer;
    private final int indexMask;
    private final int indexShift;

    public MultiProducerSequencer(int ringBufferSize, final BlockingWaitStrategyV4 blockingWaitStrategyV4) {
        this.ringBufferSize = ringBufferSize;
        this.blockingWaitStrategyV4 = blockingWaitStrategyV4;
        this.availableBuffer = new int[ringBufferSize];
        this.indexMask = this.ringBufferSize - 1;
        this.indexShift = log2(ringBufferSize);
        initialiseAvailableBuffer();
    }


    @Override
    public long next() {
        do {
            // 保存申请前的生产者序列
            long currentMaxProducerSequenceNum = currentMaxProducerSequence.getRealValue();
            // 申请之后的生产者位点
            long nextProducerSequence = currentMaxProducerSequenceNum + 1;

            long gatingSequence = SequenceUtilV4.getMinimumSequence(currentMaxProducerSequenceNum, this.gatingConsumerSequence);
            // 申请之后的生产者位点是否超过了最慢的消费者位点一圈
            if(nextProducerSequence > gatingSequence + this.ringBufferSize){
                // 如果确实超过了一圈，则生产者无法获取队列空间
                LockSupport.parkNanos(1);
                // park短暂阻塞后重新进入循环
                // continue;
            }else {
                if (this.currentMaxProducerSequence.compareAndSet(currentMaxProducerSequenceNum, nextProducerSequence)) {
                    // 由于是多生产者序列，可能存在多个生产者同时执行next方法申请序列，因此只有cas成功的线程才视为申请成功，可以跳出循环
                    return nextProducerSequence;
                }

                // cas更新失败，重新循环获取最新的消费位点
                // continue;
            }
        }while (true);
    }

    @Override
    public void publish(long publishIndex) {

    }

    @Override
    public void addConsumerSequence(SequenceV4 consumerSequenceV4) {

    }

    @Override
    public void addConsumerSequenceList(List<SequenceV4> consumerSequenceV4) {

    }

    @Override
    public int getRingBufferSize() {
        return 0;
    }

    @Override
    public SequenceBarrierV4 newBarrier() {
        return null;
    }

    @Override
    public SequenceBarrierV4 newBarrier(SequenceV4... dependenceSequences) {
        return null;
    }

    @Override
    public SequenceV4 getCurrentMaxProducerSequence() {
        return null;
    }

    private void initialiseAvailableBuffer() {
        for (int i = availableBuffer.length - 1; i >= 0; i--) {
            this.availableBuffer[i] = -1;
        }
    }

    public static int log2(int i) {
        int r = 0;
        while ((i >>= 1) != 0) {
            ++r;
        }
        return r;
    }
}
