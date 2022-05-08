package disruptor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 序列对象
 * */
public class Sequence {

    private final AtomicLong value;

    public Sequence() {
        this.value = new AtomicLong();
    }

    public Sequence(long value) {
        this.value = new AtomicLong(value);
    }

    public long getRealValue(){
        return value.longValue();
    }

    public void setRealValue(long value){
        this.value.lazySet(value);
    }
}
