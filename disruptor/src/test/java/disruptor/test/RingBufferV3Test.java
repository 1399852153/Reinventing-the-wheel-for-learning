package disruptor.test;

import disruptor.model.*;
import disruptor.util.LogUtil;
import disruptor.v3.EventProcessorV3;
import disruptor.v3.MyRingBufferV3;
import disruptor.v3.SequenceBarrierV3;
import disruptor.v3.SequenceV3;

public class RingBufferV3Test {

    public static void main(String[] args) {
        MyRingBufferV3<OrderModel> myRingBuffer = new MyRingBufferV3<>(4,new OrderEventProducer());

        int produceCount = 10;

        // 消费者1
        EventProcessorV3<OrderModel> eventProcessor =
                new EventProcessorV3<>(myRingBuffer, new OrderEventConsumer(produceCount),myRingBuffer.getSequenceBarrier());
        SequenceV3 consumeSequence = eventProcessor.getCurrentConsumeSequence();
        myRingBuffer.addConsumerSequence(consumeSequence);
        new Thread(eventProcessor).start();

        // 消费者2依赖消费者1，所以基于消费者1的sequence生成barrier
        SequenceBarrierV3 step2Barrier = myRingBuffer.newBarrier(consumeSequence);
        // 消费者2
        EventProcessorV3<OrderModel> eventProcessor2 =
                new EventProcessorV3<>(myRingBuffer, new OrderEventConsumer2(produceCount),step2Barrier);
        SequenceV3 consumeSequence2 = eventProcessor2.getCurrentConsumeSequence();
        myRingBuffer.addConsumerSequence(consumeSequence2);
        new Thread(eventProcessor2).start();

        // 消费者3依赖消费者2，所以基于消费者2的sequence生成barrier
        SequenceBarrierV3 step3Barrier = myRingBuffer.newBarrier(consumeSequence2);
        // 消费者3
        EventProcessorV3<OrderModel> eventProcessor3 =
                new EventProcessorV3<>(myRingBuffer, new OrderEventConsumer3(produceCount),step3Barrier);
        SequenceV3 consumeSequence3 = eventProcessor3.getCurrentConsumeSequence();
        myRingBuffer.addConsumerSequence(consumeSequence3);
        new Thread(eventProcessor3).start();

        for(int i=0; i<produceCount; i++) {
            long nextIndex = myRingBuffer.next();
            OrderModel orderEvent = myRingBuffer.get(nextIndex);
            orderEvent.setMessage("message-"+i);
            orderEvent.setPrice(i * 10);
            LogUtil.logWithThreadName("生产者发布事件：" + orderEvent);
            myRingBuffer.publish(nextIndex);
        }
    }
}
