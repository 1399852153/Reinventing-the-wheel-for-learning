package disruptor.v3;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 序列对象
 * */
public class SequenceV2 {

    private final AtomicLong value;

    public SequenceV2() {
        this.value = new AtomicLong();
    }

    public SequenceV2(long value) {
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
        return "SequenceV2{" +
                "value=" + value +
                '}';
    }
}
