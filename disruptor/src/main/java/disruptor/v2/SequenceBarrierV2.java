package disruptor.v2;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ringBuffer的序列屏障
 * */
public class SequenceBarrierV2<T> {

    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();

    private final MyRingBufferV2<T> myRingBuffer;

    public SequenceBarrierV2(MyRingBufferV2<T> myRingBuffer) {
        this.myRingBuffer = myRingBuffer;
    }

    /**
     * 获得可用的最大消费者下标(如果没有)
     * */
    public long getAvailableConsumeSequence(long currentConsumeSequence) throws InterruptedException {
        // 如果ringBuffer的生产者下标小于当前消费者所需的下标
        if (myRingBuffer.getSingleProducerSequencer().getCurrentProducerSequence() < currentConsumeSequence) {
            // 说明目前 消费者消费速度大于生产者生产速度

            lock.lock();
            try
            {
                while (myRingBuffer.getSingleProducerSequencer().getCurrentProducerSequence() < currentConsumeSequence) {
                    // 阻塞等待
                    processorNotifyCondition.await();
                }
            }
            finally {
                lock.unlock();
            }
        }
        return myRingBuffer.getSingleProducerSequencer().getCurrentProducerSequence();
    }

    public void signalAllWhenBlocking(){
        lock.lock();
        try
        {
            processorNotifyCondition.signalAll();
        }
        finally
        {
            lock.unlock();
        }
    }
}
