package disruptor.test;

import disruptor.model.OrderEventConsumer;
import disruptor.model.OrderEventProducer;
import disruptor.model.OrderModel;
import disruptor.v2.EventProcessorV2;
import disruptor.v2.MyRingBufferV2;
import disruptor.v2.SequenceV2;
import disruptor.v2.SingleProducerSequencerV2;

public class RingBufferV2Test {

    public static void main(String[] args) {
        SingleProducerSequencerV2 singleProducerSequencer = new SingleProducerSequencerV2(10);
        MyRingBufferV2<OrderModel> myRingBuffer = new MyRingBufferV2<>(singleProducerSequencer,new OrderEventProducer());
        EventProcessorV2<OrderModel> eventProcessor =
                new EventProcessorV2<>(myRingBuffer,new OrderEventConsumer());
        SequenceV2 consumeSequence = eventProcessor.getCurrentConsumeSequence();
        myRingBuffer.addConsumerSequence(consumeSequence);

        new Thread(eventProcessor).start();

        int produceCount = 1000;
        for(int i=0; i<produceCount; i++) {
            long nextIndex = singleProducerSequencer.next();
            OrderModel orderEvent = myRingBuffer.get(nextIndex);
            orderEvent.setMessage("message-"+i);
            orderEvent.setPrice(i * 10);
            myRingBuffer.publish(nextIndex);
        }
    }
}
