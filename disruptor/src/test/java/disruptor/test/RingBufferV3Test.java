package disruptor.test;

import disruptor.model.*;
import disruptor.util.LogUtil;
import disruptor.v3.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class RingBufferV3Test {

    public static void main(String[] args) {
        MyRingBufferV3<OrderModel> myRingBuffer = new MyRingBufferV3<>(4,new OrderEventProducer());

        int produceCount = 10;

        // 消费者1
        BatchEventProcessorV3<OrderModel> eventProcessor =
                new BatchEventProcessorV3<>(myRingBuffer, new OrderEventConsumer(produceCount),myRingBuffer.getSequenceBarrier());
        SequenceV3 consumeSequence = eventProcessor.getCurrentConsumeSequence();
        myRingBuffer.addConsumerSequence(consumeSequence);
        new Thread(eventProcessor).start();

        // worker消费者依赖消费者1，所以基于消费者1的sequence生成barrier
        SequenceBarrierV3 step2Barrier = myRingBuffer.newBarrier(consumeSequence);

        WorkerPool<OrderModel> workerPool = new WorkerPool<>(myRingBuffer,step2Barrier,
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
        SequenceV3[] workerConsumeSequenceList = workerPool.getCurrentWorkerSequences();
        myRingBuffer.addConsumerSequence(workerConsumeSequenceList);

        // 消费者2依赖worker消费者，所以基于worker消费者的sequence集合生成barrier
        SequenceBarrierV3 step3Barrier = myRingBuffer.newBarrier(workerConsumeSequenceList);

        // 消费者2
        BatchEventProcessorV3<OrderModel> eventProcessor2 =
                new BatchEventProcessorV3<>(myRingBuffer, new OrderEventConsumer2(produceCount),step3Barrier);
        SequenceV3 consumeSequence2 = eventProcessor2.getCurrentConsumeSequence();
        myRingBuffer.addConsumerSequence(consumeSequence2);
        new Thread(eventProcessor2).start();

//        // 消费者3依赖消费者2，所以基于消费者2的sequence生成barrier
//        SequenceBarrierV3 step3Barrier = myRingBuffer.newBarrier(consumeSequence2);
//        // 消费者3
//        BatchEventProcessorV3<OrderModel> eventProcessor3 =
//                new BatchEventProcessorV3<>(myRingBuffer, new OrderEventConsumer3(produceCount),step3Barrier);
//        SequenceV3 consumeSequence3 = eventProcessor3.getCurrentConsumeSequence();
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
