package disruptor;

import disruptor.model.OrderEventConsumer;
import disruptor.model.OrderEventProducer;
import disruptor.model.OrderModel;

public class RingBufferTest {

    public static void main(String[] args) {
        SingleProducerSequencer singleProducerSequencer = new SingleProducerSequencer(10);
        MyRingBuffer<OrderModel> myRingBuffer = new MyRingBuffer<>(singleProducerSequencer,new OrderEventProducer());
        EventProcessor<OrderModel> eventProcessor =
                new EventProcessor<>(myRingBuffer,new OrderEventConsumer());
        Sequence consumeSequence = eventProcessor.getCurrentConsumeSequence();
        myRingBuffer.setConsumerSequence(consumeSequence);

        new Thread(eventProcessor).start();

        int produceCount = 100;
        for(int i=0; i<produceCount; i++) {
            long nextIndex = singleProducerSequencer.next();
            OrderModel orderEvent = myRingBuffer.get(nextIndex);
            orderEvent.setMessage("message-"+i);
            orderEvent.setPrice(i * 10);
        }
    }
}
