package disruptor.v2;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BlockingWaitStrategy {
    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();


    public long waitFor(long currentConsumeSequence, SequenceV2 currentSequence,
                        List<SequenceV2> dependentSequences) throws InterruptedException {

        // 如果ringBuffer的生产者下标小于当前消费者所需的下标
        if (currentSequence.getRealValue() < currentConsumeSequence) {
            // 说明目前 消费者消费速度大于生产者生产速度

            lock.lock();
            try {
                while (currentSequence.getRealValue() < currentConsumeSequence) {
                    // 阻塞等待
                    processorNotifyCondition.await();
                }
            }
            finally {
                lock.unlock();
            }
        }

        // todo 不得超过dependentSequences中最小的值

        return currentSequence.getRealValue();
    }

    public void signalAllWhenBlocking(){
        lock.lock();
        try {
            processorNotifyCondition.signalAll();
        }
        finally {
            lock.unlock();
        }
    }
}
