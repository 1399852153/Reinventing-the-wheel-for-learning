package disruptor.v3;

import disruptor.api.MyEventConsumer;
import disruptor.util.LogUtil;
import disruptor.v3.api.EventProcessor;

public class BatchEventProcessorV3<T> implements Runnable, EventProcessor {

    private final SequenceV3 currentConsumeSequence = new SequenceV3(-1);
    private final MyRingBufferV3<T> myRingBuffer;
    private final MyEventConsumer<T> myEventConsumer;
    private final SequenceBarrierV3 sequenceBarrier;

    public BatchEventProcessorV3(MyRingBufferV3<T> myRingBuffer, MyEventConsumer<T> myEventConsumer, SequenceBarrierV3 sequenceBarrier) {
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
    public SequenceV3 getCurrentConsumeSequence() {
        return currentConsumeSequence;
    }
}
