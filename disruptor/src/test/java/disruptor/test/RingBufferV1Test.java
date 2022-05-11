package disruptor.test;

import disruptor.model.OrderEventConsumer;
import disruptor.model.OrderEventProducer;
import disruptor.model.OrderModel;
import disruptor.v1.EventProcessorV1;
import disruptor.v1.MyRingBufferV1;
import disruptor.v1.SequenceV1;
import disruptor.v1.SingleProducerSequencerV1;

public class RingBufferV1Test {

    public static void main(String[] args) {
        SingleProducerSequencerV1 singleProducerSequencerV1 = new SingleProducerSequencerV1(10);
        MyRingBufferV1<OrderModel> myRingBufferV1 = new MyRingBufferV1<>(singleProducerSequencerV1,new OrderEventProducer());

        int produceCount = 1000;

        EventProcessorV1<OrderModel> eventProcessorV1 =
                new EventProcessorV1<>(myRingBufferV1,new OrderEventConsumer(produceCount));
        SequenceV1 consumeSequenceV1 = eventProcessorV1.getCurrentConsumeSequence();
        myRingBufferV1.setConsumerSequence(consumeSequenceV1);

        new Thread(eventProcessorV1).start();

        for(int i=0; i<produceCount; i++) {
            long nextIndex = singleProducerSequencerV1.next();
            OrderModel orderEvent = myRingBufferV1.get(nextIndex);
            orderEvent.setMessage("message-"+i);
            orderEvent.setPrice(i * 10);
            myRingBufferV1.publish(nextIndex);
        }
    }
}
