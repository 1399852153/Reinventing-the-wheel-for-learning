package disruptor;

import disruptor.api.MyEventConsumer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventProcessor<T> implements Runnable{

    private final long currentConsumeIndex = 0;
    private MyRingBuffer<T> myRingBuffer;

    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();
    private final MyEventConsumer<T> myEventConsumer;

    public EventProcessor(MyRingBuffer<T> myRingBuffer,MyEventConsumer<T> myEventConsumer) {
        this.myRingBuffer = myRingBuffer;
        this.myEventConsumer = myEventConsumer;
    }

    @Override
    public void run() {
        // 主循环，不断的尝试获取事件并进行消费
        while(true) {
            // 下一个需要消费的下标
            long nextConsumerIndex = currentConsumeIndex + 1;

            try {
                long availableConsumeIndex = getAvailableConsumeIndex();

                while (nextConsumerIndex <= availableConsumeIndex) {
                    // 取出可以消费的下标对应的事件，交给eventConsumer消费
                    T event = myRingBuffer.get(nextConsumerIndex);
                    this.myEventConsumer.consume(event, nextConsumerIndex, nextConsumerIndex == availableConsumeIndex);
                    // 批处理，一次主循环消费N个事件（下标加1，获取下一个）
                    nextConsumerIndex++;
                }
            } catch (InterruptedException e) {
                // 异常处理
            }
        }
    }

    /**
     * 获得可用的最大消费者下标
     * */
    private long getAvailableConsumeIndex() throws InterruptedException {
        // 如果ringBuffer的生产者下标小于当前消费者所需的下标
        if (myRingBuffer.getSingleProducerSequencer().getCurrentProducerIndex() < currentConsumeIndex) {
            // 说明目前 消费者消费速度大于生产者生产速度

            lock.lock();
            try
            {
                while (myRingBuffer.getSingleProducerSequencer().getCurrentProducerIndex() < currentConsumeIndex) {
                    // 阻塞等待
                    processorNotifyCondition.await();
                }
            }
            finally {
                lock.unlock();
            }
        }
        return myRingBuffer.getSingleProducerSequencer().getCurrentProducerIndex();
    }
}
