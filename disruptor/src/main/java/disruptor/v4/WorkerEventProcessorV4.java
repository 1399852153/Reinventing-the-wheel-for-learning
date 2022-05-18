package disruptor.v4;

import disruptor.api.MyEventConsumer;

public class WorkerEventProcessorV4<T> implements Runnable, EventProcessorV4 {

    private final SequenceV4 currentConsumeSequence = new SequenceV4(-1);
    private final MyRingBufferV4<T> myRingBuffer;
    private final MyEventConsumer<T> myEventConsumer;
    private final SequenceBarrierV4 sequenceBarrier;
    private final SequenceV4 workGroupSequence;


    public WorkerEventProcessorV4(MyRingBufferV4<T> myRingBuffer,
                                  MyEventConsumer<T> myEventConsumer,
                                  SequenceBarrierV4 sequenceBarrier,
                                  SequenceV4 workGroupSequence) {
        this.myRingBuffer = myRingBuffer;
        this.myEventConsumer = myEventConsumer;
        this.sequenceBarrier = sequenceBarrier;
        this.workGroupSequence = workGroupSequence;
    }

    @Override
    public void run() {

        long nextConsumerIndex = this.currentConsumeSequence.getRealValue() + 1;
        long cachedAvailableSequence = Long.MIN_VALUE;

        // 最近是否处理过了序列
        boolean processedSequence = true;


        while (true) {
            try {
                if(processedSequence) {
                    // 如果已经处理过序列，则重新cas的争抢一个新的待消费序列
                    do {
                        nextConsumerIndex = this.workGroupSequence.getRealValue() + 1L;
                        // 由于currentConsumeSequence会被注册到生产者侧，因此需要始终和workGroupSequence worker组的实际sequence保持协调
                        // 即当前worker的消费序列 = workGroupSequence
                        this.currentConsumeSequence.setRealValue(nextConsumerIndex - 1L);

                        // cas更新，保证每个worker线程都会获取到唯一的一个sequence
                    } while (!workGroupSequence.compareAndSet(nextConsumerIndex - 1L, nextConsumerIndex));

                    // 争抢到了一个新的待消费序列，但还未实际进行消费（标记为false）
                    processedSequence = false;
                }else{
                    // processedSequence == false(手头上存在一个还未消费的序列)
                    // 走到这里说明之前拿到了一个新的消费序列，但是由于nextConsumerIndex <= cachedAvailableSequence，被阻塞住了
                    // 需要先把手头上的这个序列给消费掉，才能继续拿下一个消费序列
                }

                // cachedAvailableSequence只会存在两种情况
                // 1 第一次循环，初始化为Long.MIN_VALUE，则必定会走到else分支中
                // 2 非第一次循环，则cachedAvailableSequence为序列屏障所允许的最大可消费序列

                if (nextConsumerIndex <= cachedAvailableSequence) {
                    // 争抢到的消费序列是满足要求的（小于序列屏障值，被序列屏障允许的），则调用消费者进行实际的消费

                    // 取出可以消费的下标对应的事件，交给eventConsumer消费
                    T event = myRingBuffer.get(nextConsumerIndex);
                    this.myEventConsumer.consume(event, nextConsumerIndex, false);

                    // 实际调用消费者进行消费了，标记为true.这样一来就可以在下次循环中cas争抢下一个新的消费序列了
                    processedSequence = true;
                } else {
                    // 1 第一次循环会获取当前序列屏障的最大可消费序列
                    // 2 非第一次循环，说明争抢到的序列超过了屏障序列的最大值，等待生产者推进到争抢到的sequence
                    cachedAvailableSequence = sequenceBarrier.getAvailableConsumeSequence(nextConsumerIndex);
                }
            } catch (final Throwable ex) {
                // 消费者消费时发生了异常，也认为是成功消费了，避免阻塞消费序列
                // 下次循环会cas争抢一个新的消费序列
                processedSequence = true;
            }
        }
    }

    @Override
    public SequenceV4 getCurrentConsumeSequence() {
        return this.currentConsumeSequence;
    }


}
