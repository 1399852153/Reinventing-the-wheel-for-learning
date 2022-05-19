package disruptor.v4;

import disruptor.v4.api.ProducerSequencer;
import disruptor.v4.util.SequenceUtilV4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * 多生产者序列器
 * 线程安全，允许多个线程并发调用next、publish等方法
 * */
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
        setAvailable(publishIndex);
        this.blockingWaitStrategyV4.signalAllWhenBlocking();
    }

    @Override
    public void addConsumerSequence(SequenceV4 consumerSequenceV4) {
        this.gatingConsumerSequence.add(consumerSequenceV4);
    }

    @Override
    public void addConsumerSequenceList(List<SequenceV4> consumerSequenceV4) {
        this.gatingConsumerSequence.addAll(consumerSequenceV4);
    }

    @Override
    public int getRingBufferSize() {
        return this.ringBufferSize;
    }

    @Override
    public SequenceBarrierV4 newBarrier() {
        return new SequenceBarrierV4(this.currentMaxProducerSequence,this.blockingWaitStrategyV4,new ArrayList<>());
    }

    @Override
    public SequenceBarrierV4 newBarrier(SequenceV4... dependenceSequences) {
        return new SequenceBarrierV4(this.currentMaxProducerSequence,this.blockingWaitStrategyV4,new ArrayList<>());
    }

    @Override
    public SequenceV4 getCurrentMaxProducerSequence() {
        return this.currentMaxProducerSequence;
    }

    @Override
    public boolean isAvailable(long sequence) {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        return this.availableBuffer[index] == flag;
    }

    @Override
    public long getHighestPublishedSequence(long lowBound, long availableSequence) {
        // lowBound是消费者传入的，保证是已经明确发布了的最小生产者序列号
        // 因此，从lowBound开始，向后寻找,有两种情况
        // 1 在lowBound到availableSequence中间存在未发布的下标(isAvailable(sequence) == false)，
        // 那么，找到的这个未发布下标的前一个序列号，就是当前最大的已经发布了的序列号（可以被消费者正常消费）
        // 2 在lowBound到availableSequence中间不存在未发布的下标，那么就和单生产者的情况一样
        // 包括availableSequence以及之前的序列号都已经发布过了，availableSequence就是当前最大的已发布的序列号
        for(long sequence=lowBound; sequence<=availableSequence; sequence++){
            if (!isAvailable(sequence)) {
                // 属于上述的情况1，lowBound和availableSequence中间存在未发布的序列号
                return sequence - 1;
            }
        }

        // 属于上述的情况2，lowBound和availableSequence中间不存在未发布的序列号
        return availableSequence;
    }

    private void initialiseAvailableBuffer() {
        for (int i = availableBuffer.length - 1; i >= 0; i--) {
            this.availableBuffer[i] = -1;
        }
    }

    private void setAvailable(long sequence){
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        this.availableBuffer[index] = flag;
    }

    private int calculateAvailabilityFlag(long sequence) {
        return (int) (sequence >>> indexShift);
    }

    private int calculateIndex(long sequence) {
        return ((int) sequence) & indexMask;
    }

    public static int log2(int i) {
        int r = 0;
        while ((i >>= 1) != 0) {
            ++r;
        }
        return r;
    }

//    public static void main(String[] args) {
//        int bufferSize = 8;
//        int indexMask = bufferSize - 1;
//        int indexShift = log2(bufferSize);
//
//        for(int sequence=0; sequence < 1000; sequence++){
//            int index = sequence & indexMask;
//            int flag = sequence >>> indexShift;
//            System.out.println("sequence=" + sequence + " index=" + index + " flag=" + flag);
//        }
//    }
}
