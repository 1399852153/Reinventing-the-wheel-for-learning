package disruptor.v3;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 序列对象
 * */
public class SequenceV3 {

    private final AtomicLong value;

    public SequenceV3() {
        this.value = new AtomicLong();
    }

    public SequenceV3(long value) {
        this.value = new AtomicLong(value);
    }

    public long getRealValue(){
        return value.longValue();
    }

    public void setRealValue(long value){
        this.value.set(value);
    }

    public boolean compareAndSet(long expect, long update){
        return value.compareAndSet(expect,update);
    }

    @Override
    public String toString() {
        return "SequenceV2{" +
                "value=" + value +
                '}';
    }
}