package disruptor.v1;

import disruptor.api.MyEventConsumer;

public class EventProcessorV1<T> implements Runnable{

    private final SequenceV1 currentConsumeSequenceV1 = new SequenceV1(-1);
    private final MyRingBufferV1<T> myRingBufferV1;
    private final MyEventConsumer<T> myEventConsumer;
    private final SequenceBarrierV1<T> sequenceBarrierV1;

    public EventProcessorV1(MyRingBufferV1<T> myRingBufferV1, MyEventConsumer<T> myEventConsumer) {
        this.myRingBufferV1 = myRingBufferV1;
        this.myEventConsumer = myEventConsumer;
        this.sequenceBarrierV1 = myRingBufferV1.getSequenceBarrier();
    }

    @Override
    public void run() {

        // 下一个需要消费的下标
        long nextConsumerIndex = currentConsumeSequenceV1.getRealValue() + 1;

        // 消费者线程主循环逻辑，不断的尝试获取事件并进行消费
        while(true) {
            try {
                long availableConsumeIndex = this.sequenceBarrierV1.getAvailableConsumeSequence(this.currentConsumeSequenceV1.getRealValue());

                while (nextConsumerIndex <= availableConsumeIndex) {
                    // 取出可以消费的下标对应的事件，交给eventConsumer消费
                    T event = myRingBufferV1.get(nextConsumerIndex);
                    this.myEventConsumer.consume(event, nextConsumerIndex, nextConsumerIndex == availableConsumeIndex);
                    // 批处理，一次主循环消费N个事件（下标加1，获取下一个）
                    nextConsumerIndex++;
                }

                // 更新当前消费者的消费的序列
                this.currentConsumeSequenceV1.setRealValue(nextConsumerIndex);
                System.out.println("更新当前消费者的消费的序列:" + nextConsumerIndex);
            } catch (Exception e) {
                // 发生异常，消费进度依然推进
                this.currentConsumeSequenceV1.setRealValue(nextConsumerIndex);
                nextConsumerIndex++;
            }
        }
    }

    public SequenceV1 getCurrentConsumeSequence() {
        return currentConsumeSequenceV1;
    }
}
