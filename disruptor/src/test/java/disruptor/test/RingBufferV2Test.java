package disruptor.test;

import disruptor.model.*;
import disruptor.util.LogUtil;
import disruptor.v2.EventProcessorV2;
import disruptor.v2.MyRingBufferV2;
import disruptor.v2.SequenceV2;
import disruptor.v2.SingleProducerSequencerV2;

public class RingBufferV2Test {

    public static void main(String[] args) {
        SingleProducerSequencerV2 singleProducerSequencer = new SingleProducerSequencerV2(3);
        MyRingBufferV2<OrderModel> myRingBuffer = new MyRingBufferV2<>(singleProducerSequencer,new OrderEventProducer());

        int produceCount = 10;

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

//        {
//            // 消费者3
//            EventProcessorV2<OrderModel> eventProcessor =
//                    new EventProcessorV2<>(myRingBuffer, new OrderEventConsumer3());
//            SequenceV2 consumeSequence = eventProcessor.getCurrentConsumeSequence();
//            myRingBuffer.addConsumerSequence(consumeSequence);
//            new Thread(eventProcessor).start();
//        }

        for(int i=0; i<produceCount; i++) {
            long nextIndex = singleProducerSequencer.next();
            OrderModel orderEvent = myRingBuffer.get(nextIndex);
            orderEvent.setMessage("message-"+i);
            orderEvent.setPrice(i * 10);
            LogUtil.logWithThreadName("生产者发布事件：" + orderEvent);
            myRingBuffer.publish(nextIndex);
        }
    }
}
