package disruptor.v4;

import disruptor.api.MyEventConsumer;
import disruptor.util.LogUtil;
import disruptor.v4.api.EventProcessorV4;

public class BatchEventProcessorV4<T> implements Runnable, EventProcessorV4 {

    private final SequenceV4 currentConsumeSequence = new SequenceV4(-1);
    private final MyRingBufferV4<T> myRingBuffer;
    private final MyEventConsumer<T> myEventConsumer;
    private final SequenceBarrierV4 sequenceBarrier;

    public BatchEventProcessorV4(MyRingBufferV4<T> myRingBuffer, MyEventConsumer<T> myEventConsumer, SequenceBarrierV4 sequenceBarrier) {
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
    public SequenceV4 getCurrentConsumeSequence() {
        return currentConsumeSequence;
    }
}
