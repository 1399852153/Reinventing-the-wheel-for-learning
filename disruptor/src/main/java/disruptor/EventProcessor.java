package disruptor;

import disruptor.api.MyEventConsumer;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventProcessor<T> implements Runnable{

    private long currentConsumeIndex = 0;
    private MyRingBuffer<T> myRingBuffer;
    private final MyEventConsumer<T> myEventConsumer;
    private final SequenceBarrier<T> sequenceBarrier;

    public EventProcessor(MyRingBuffer<T> myRingBuffer, MyEventConsumer<T> myEventConsumer, SequenceBarrier<T> sequenceBarrier) {
        this.myRingBuffer = myRingBuffer;
        this.myEventConsumer = myEventConsumer;
        this.sequenceBarrier = sequenceBarrier;
    }

    @Override
    public void run() {

        // 下一个需要消费的下标
        long nextConsumerIndex = currentConsumeIndex + 1;

        // 消费者线程主循环逻辑，不断的尝试获取事件并进行消费
        while(true) {
            try {
                long availableConsumeIndex = this.sequenceBarrier.getAvailableConsumeIndex(this.currentConsumeIndex);

                while (nextConsumerIndex <= availableConsumeIndex) {
                    // 取出可以消费的下标对应的事件，交给eventConsumer消费
                    T event = myRingBuffer.get(nextConsumerIndex);
                    this.myEventConsumer.consume(event, nextConsumerIndex, nextConsumerIndex == availableConsumeIndex);
                    // 批处理，一次主循环消费N个事件（下标加1，获取下一个）
                    nextConsumerIndex++;
                }
            } catch (Exception e) {
                // 发生异常，消费进度依然推进
                this.currentConsumeIndex = nextConsumerIndex;
                nextConsumerIndex++;
            }
        }
    }


}
