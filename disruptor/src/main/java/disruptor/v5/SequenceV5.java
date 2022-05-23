package disruptor.v5;

import jdk.internal.org.objectweb.asm.tree.analysis.Value;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 序列对象
 * */
public class SequenceV5 {

    /**
     * 解决伪共享 左半部分填充
     * */
    private long lp1, lp2, lp3, lp4, lp5, lp6, lp7;

    /**
     * 真正的核心数据value，左右填充避免了伪共享
     * */
    private volatile long value;

    /**
     * 解决伪共享 右半部分填充
     * */
    private long rp1, rp2, rp3, rp4, rp5, rp6, rp7;

    static final long INITIAL_VALUE = -1L;
    private static final Unsafe UNSAFE;
    private static final long VALUE_OFFSET;

    static {
        try {
            // 由于提供给cas内存中字段偏移量的unsafe类只能在被jdk信任的类中直接使用，这里使用反射来绕过这一限制
            Field getUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            getUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) getUnsafe.get(null);
            VALUE_OFFSET = UNSAFE.objectFieldOffset(SequenceV5.class.getDeclaredField("value"));
        }
        catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SequenceV5() {
        this(INITIAL_VALUE);
    }

    public SequenceV5(long initialValue) {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, initialValue);
    }

    public long getRealValue(){
        return value;
    }

    public void setRealValue(long value){
        this.value = value;
    }

    public void lazySetRealValue(final long value) {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, value);
    }

    public boolean compareAndSet(long expect, long update){
        return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expect, update);
    }

    @Override
    public String toString() {
        return "SequenceV4{" +
                "value=" + value +
                '}';
    }
}
