package disruptor.v1;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 序列对象
 * */
public class SequenceV1 {

    private final AtomicLong value;

    public SequenceV1() {
        this.value = new AtomicLong();
    }

    public SequenceV1(long value) {
        this.value = new AtomicLong(value);
    }

    public long getRealValue(){
        return value.longValue();
    }

    public void setRealValue(long value){
        this.value.set(value);
    }

    @Override
    public String toString() {
        return "SequenceV1{" +
                "value=" + value +
                '}';
    }
}
