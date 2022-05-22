package disruptor.test;

import disruptor.api.ProducerType;
import disruptor.model.*;
import disruptor.util.LogUtil;
import disruptor.v4.BlockingWaitStrategyV4;
import disruptor.v4.MyDisruptor;
import disruptor.v4.MyRingBufferV4;

import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RingBufferV4Test {

    public static void main(String[] args) throws InterruptedException {
        MyDisruptor<OrderModel> myDisruptor = new MyDisruptor<>(
                new OrderEventProducer(), 8, new ThreadPoolExecutor(10, 20,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>()),
                ProducerType.SINGLE,
                new BlockingWaitStrategyV4()
        );

        int produceCount = 10;
        myDisruptor.handleEventsWith(new OrderEventConsumer(produceCount))
                .then(new OrderEventConsumer2(produceCount))
                .then(new OrderEventConsumer3(produceCount));

        myDisruptor.start();

        Thread.sleep(1000L);
        MyRingBufferV4<OrderModel> myRingBuffer = myDisruptor.getRingBuffer();
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
