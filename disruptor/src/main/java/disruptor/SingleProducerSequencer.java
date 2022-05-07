package disruptor;

import java.util.concurrent.locks.LockSupport;

/**
 * 单生产者序列器
 * */
public class SingleProducerSequencer {

    private final int ringBufferSize;
    private long currentProducerIndex = -1;
    private long slowestConsumerIndex = -1;

    public SingleProducerSequencer(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    /**
     * 申请下一个序列的权限
     * */
    public long next(){
        // 申请之后的生产者位点
        long nextProducerIndex = this.currentProducerIndex + 1;

        // 申请之后的生产者位点是否超过了最慢的消费者位点一圈
        while(nextProducerIndex > this.slowestConsumerIndex + this.ringBufferSize){
            // 如果确实超过了一圈，则生产者无法获取队列空间，无限循环的park超时阻塞
            LockSupport.parkNanos(1L);
        }

        this.currentProducerIndex = nextProducerIndex;
        return nextProducerIndex;
    }

    public long getCurrentProducerIndex() {
        return currentProducerIndex;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }
}
