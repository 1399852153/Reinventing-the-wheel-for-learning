package disruptor.test;

import disruptor.api.ProducerType;
import disruptor.model.*;
import disruptor.util.LogUtil;
import disruptor.v5.BlockingWaitStrategyV5;
import disruptor.v5.MyDisruptor;
import disruptor.v5.MyRingBufferV5;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RingBufferV5Test {

    public static void main(String[] args) throws InterruptedException {
        MyDisruptor<OrderModel> myDisruptor = new MyDisruptor<>(
                new OrderEventProducer(), 8, new ThreadPoolExecutor(10, 20,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>()),
                ProducerType.SINGLE,
                new BlockingWaitStrategyV5()
        );

        int produceCount = 10;
        OrderEventConsumer orderEventConsumer = new OrderEventConsumer(produceCount);
        OrderEventConsumer2 orderEventConsumer2 = new OrderEventConsumer2(produceCount);
        OrderEventConsumer3 orderEventConsumer3 = new OrderEventConsumer3(produceCount);

        myDisruptor.handleEventsWith(orderEventConsumer)
                .then(orderEventConsumer2)
                .then(orderEventConsumer3);

        myDisruptor.start();

        Thread.sleep(1000L);
        MyRingBufferV5<OrderModel> myRingBuffer = myDisruptor.getRingBuffer();
        for(int i=0; i<produceCount; i++) {
            long nextIndex = myRingBuffer.next();
            OrderModel orderEvent = myRingBuffer.get(nextIndex);
            orderEvent.setMessage("message-"+i);
            orderEvent.setPrice(i * 10);
            LogUtil.logWithThreadName("生产者发布事件：" + orderEvent);
            myRingBuffer.publish(nextIndex);
        }

        Thread.sleep(3000L);
        orderEventConsumer.clear();
        orderEventConsumer2.clear();
        orderEventConsumer3.clear();

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
