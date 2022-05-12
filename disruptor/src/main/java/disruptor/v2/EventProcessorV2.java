package disruptor.v2;

import disruptor.api.MyEventConsumer;
import disruptor.util.LogUtil;

public class EventProcessorV2<T> implements Runnable{

    private final SequenceV2 currentConsumeSequence = new SequenceV2(-1);
    private final MyRingBufferV2<T> myRingBuffer;
    private final MyEventConsumer<T> myEventConsumer;
    private final SequenceBarrierV2 sequenceBarrier;

    public EventProcessorV2(MyRingBufferV2<T> myRingBuffer, MyEventConsumer<T> myEventConsumer) {
        this.myRingBuffer = myRingBuffer;
        this.myEventConsumer = myEventConsumer;
        this.sequenceBarrier = myRingBuffer.getSequenceBarrier();
    }

    @Override
    public void run() {

        // 下一个需要消费的下标
        long nextConsumerIndex = currentConsumeSequence.getRealValue() + 1;

        // 消费者线程主循环逻辑，不断的尝试获取事件并进行消费
        while(true) {
            try {
                long availableConsumeIndex = this.sequenceBarrier.getAvailableConsumeSequence(this.currentConsumeSequence.getRealValue());

                while (nextConsumerIndex <= availableConsumeIndex) {
                    // 取出可以消费的下标对应的事件，交给eventConsumer消费
                    T event = myRingBuffer.get(nextConsumerIndex);
                    this.myEventConsumer.consume(event, nextConsumerIndex, nextConsumerIndex == availableConsumeIndex);
                    // 批处理，一次主循环消费N个事件（下标加1，获取下一个）
                    nextConsumerIndex++;
                }

                // 更新当前消费者的消费的序列
                this.currentConsumeSequence.setRealValue(nextConsumerIndex);
                LogUtil.logWithThreadName("更新当前消费者的消费的序列:" + nextConsumerIndex);
            } catch (Exception e) {
                // 发生异常，消费进度依然推进
                this.currentConsumeSequence.setRealValue(nextConsumerIndex);
                nextConsumerIndex++;
            }
        }
    }

    public SequenceV2 getCurrentConsumeSequence() {
        return currentConsumeSequence;
    }
}
