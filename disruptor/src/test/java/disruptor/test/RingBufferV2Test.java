package disruptor.test;

import disruptor.model.*;
import disruptor.util.LogUtil;
import disruptor.v2.EventProcessorV2;
import disruptor.v2.MyRingBufferV2;
import disruptor.v2.SequenceV2;
import disruptor.v2.SingleProducerSequencerV2;

public class RingBufferV2Test {

    public static void main(String[] args) {
        MyRingBufferV2<OrderModel> myRingBuffer = new MyRingBufferV2<>(10,new OrderEventProducer());

        int produceCount = 1000;

        {
            // 消费者1
            EventProcessorV2<OrderModel> eventProcessor =
                    new EventProcessorV2<>(myRingBuffer, new OrderEventConsumer(produceCount));
            SequenceV2 consumeSequence = eventProcessor.getCurrentConsumeSequence();
            myRingBuffer.addConsumerSequence(consumeSequence);
            new Thread(eventProcessor).start();
        }

        {
            // 消费者2
            EventProcessorV2<OrderModel> eventProcessor =
                    new EventProcessorV2<>(myRingBuffer, new OrderEventConsumer(produceCount));
            SequenceV2 consumeSequence = eventProcessor.getCurrentConsumeSequence();
            myRingBuffer.addConsumerSequence(consumeSequence);
            new Thread(eventProcessor).start();
        }

        {
            // 消费者3
            EventProcessorV2<OrderModel> eventProcessor =
                    new EventProcessorV2<>(myRingBuffer, new OrderEventConsumer(produceCount));
            SequenceV2 consumeSequence = eventProcessor.getCurrentConsumeSequence();
            myRingBuffer.addConsumerSequence(consumeSequence);
            new Thread(eventProcessor).start();
        }

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
