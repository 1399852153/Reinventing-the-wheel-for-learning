package disruptor.v5;

import disruptor.api.MyEventConsumer;
import disruptor.util.LogUtil;
import disruptor.v5.api.EventProcessorV5;

public class BatchEventProcessorV5<T> implements Runnable, EventProcessorV5 {

    private final SequenceV5 currentConsumeSequence = new SequenceV5(-1);
    private final MyRingBufferV5<T> myRingBuffer;
    private final MyEventConsumer<T> myEventConsumer;
    private final SequenceBarrierV5 sequenceBarrier;

    public BatchEventProcessorV5(MyRingBufferV5<T> myRingBuffer, MyEventConsumer<T> myEventConsumer, SequenceBarrierV5 sequenceBarrier) {
        this.myRingBuffer = myRingBuffer;
        this.myEventConsumer = myEventConsumer;
        this.sequenceBarrier = sequenceBarrier;
    }

    @Override
    public void run() {
        // 下一个需要消费的下标
        long nextConsumerIndex = currentConsumeSequence.getRealValue() + 1;

        // 消费者线程主循环逻辑，不断的尝试获取事件并进行消费
        while(true) {
            try {
                long availableConsumeIndex = this.sequenceBarrier.getAvailableConsumeSequence(nextConsumerIndex);

                while (nextConsumerIndex <= availableConsumeIndex) {
                    // 取出可以消费的下标对应的事件，交给eventConsumer消费
                    T event = myRingBuffer.get(nextConsumerIndex);
                    this.myEventConsumer.consume(event, nextConsumerIndex, nextConsumerIndex == availableConsumeIndex);
                    // 批处理，一次主循环消费N个事件（下标加1，获取下一个）
                    nextConsumerIndex++;
                }

                // 更新当前消费者的消费的序列
                this.currentConsumeSequence.setRealValue(availableConsumeIndex);
                LogUtil.logWithThreadName("更新当前消费者的消费的序列:" + availableConsumeIndex);
            } catch (final Throwable ex) {
                // 发生异常，消费进度依然推进（跳过这一批拉取的数据）
                this.currentConsumeSequence.setRealValue(nextConsumerIndex);
                nextConsumerIndex++;
            }
        }
    }

    @Override
    public SequenceV5 getCurrentConsumeSequence() {
        return currentConsumeSequence;
    }
}
