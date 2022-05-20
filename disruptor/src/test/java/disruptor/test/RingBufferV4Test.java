package disruptor.test;

import disruptor.model.*;
import disruptor.util.LogUtil;
import disruptor.v4.*;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class RingBufferV4Test {

    public static void main(String[] args) {
        MyRingBufferV4<OrderModel> myRingBuffer = new MyRingBufferV4<>(4,new OrderEventProducer());

        int produceCount = 10;

        // 消费者1
        BatchEventProcessorV4<OrderModel> eventProcessor =
                new BatchEventProcessorV4<>(myRingBuffer, new OrderEventConsumer(produceCount),myRingBuffer.getSequenceBarrier());
        SequenceV4 consumeSequence = eventProcessor.getCurrentConsumeSequence();
        myRingBuffer.addConsumerSequence(consumeSequence);
        new Thread(eventProcessor).start();

        // worker消费者依赖消费者1，所以基于消费者1的sequence生成barrier
        SequenceBarrierV4 step2Barrier = myRingBuffer.newBarrier(consumeSequence);

        WorkerPoolV4<OrderModel> workerPool = new WorkerPoolV4<>(myRingBuffer,step2Barrier,
                Arrays.asList(
                        new OrderEventWorkerConsumer(produceCount),
                        new OrderEventWorkerConsumer(produceCount),
                        new OrderEventWorkerConsumer(produceCount)));
        workerPool.start(Executors.newFixedThreadPool(3, new ThreadFactory() {
            private final AtomicInteger mCount = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,"worker" + mCount.getAndIncrement());
            }
        }));
        SequenceV4[] workerConsumeSequenceList = workerPool.getCurrentWorkerSequences();
        myRingBuffer.addConsumerSequence(workerConsumeSequenceList);

        // 消费者2依赖worker消费者，所以基于worker消费者的sequence集合生成barrier
        SequenceBarrierV4 step3Barrier = myRingBuffer.newBarrier(workerConsumeSequenceList);

        // 消费者2
        BatchEventProcessorV4<OrderModel> eventProcessor2 =
                new BatchEventProcessorV4<>(myRingBuffer, new OrderEventConsumer2(produceCount),step3Barrier);
        SequenceV4 consumeSequence2 = eventProcessor2.getCurrentConsumeSequence();
        myRingBuffer.addConsumerSequence(consumeSequence2);
        new Thread(eventProcessor2).start();

//        // 消费者3依赖消费者2，所以基于消费者2的sequence生成barrier
//        SequenceBarrierV4 step3Barrier = myRingBuffer.newBarrier(consumeSequence2);
//        // 消费者3
//        BatchEventProcessorV4<OrderModel> eventProcessor3 =
//                new BatchEventProcessorV4<>(myRingBuffer, new OrderEventConsumer3(produceCount),step3Barrier);
//        SequenceV4 consumeSequence3 = eventProcessor3.getCurrentConsumeSequence();
//        myRingBuffer.addConsumerSequence(consumeSequence3);
//        new Thread(eventProcessor3).start();


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
