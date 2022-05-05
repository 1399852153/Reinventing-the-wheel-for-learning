package disruptor;

public class SingleSequencer {

    private final int ringBufferSize;
    private int currentProducerIndex = -1;
    private int slowestConsumerIndex = -1;

    public SingleSequencer(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    private long next(){
        // 暂存之前的生产者位点
        long beforeProducerIndex = this.currentProducerIndex;

        // 申请之后的生产者位点
        long nextProducerIndex = this.currentProducerIndex + 1;

        long wrapPoint = nextProducerIndex - this.ringBufferSize;

        return 0;
    }
}
