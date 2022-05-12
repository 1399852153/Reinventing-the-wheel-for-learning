package disruptor.v2;

import disruptor.util.LogUtil;
import disruptor.v2.util.SequenceUtil;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BlockingWaitStrategy {
    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();

    public long waitFor(long currentConsumeSequence, SequenceV2 currentProducerSequence,
                        List<SequenceV2> dependentSequences) throws InterruptedException {

        if(!dependentSequences.isEmpty()){
            LogUtil.logWithThreadName("66666");
        }

        // 如果ringBuffer的生产者下标小于当前消费者所需的下标
        if (currentProducerSequence.getRealValue() < currentConsumeSequence) {
            // 说明目前 消费者消费速度大于生产者生产速度

            lock.lock();
            try {
                while (currentProducerSequence.getRealValue() < currentConsumeSequence) {
                    // 阻塞等待
                    processorNotifyCondition.await();
                }
            }
            finally {
                lock.unlock();
            }
        }

        // 跳出了上面的循环，说明生产者序列已经超过了当前所要消费的位点（currentProducerSequence > currentConsumeSequence）
        long availableSequence;
        if(!dependentSequences.isEmpty()){
            // 受制于屏障中的dependentSequences，用来控制当前消费者消费进度不得超过其链路上游的消费者进度
            while ((availableSequence = SequenceUtil.getMinimumSequence(dependentSequences)) < currentConsumeSequence) {
                // 由于消费者消费速度一般会很快，所以这里使用自旋阻塞来等待上游消费者进度推进
                // disruptor: ThreadHints.onSpinWait();
            }
        }else{
            availableSequence = currentProducerSequence.getRealValue();
        }

        return availableSequence;
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
