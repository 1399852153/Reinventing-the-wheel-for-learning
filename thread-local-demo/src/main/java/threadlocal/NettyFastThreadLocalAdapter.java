package threadlocal;

import io.netty.util.concurrent.FastThreadLocal;
import threadlocal.api.ThreadLocalApi;

public class NettyFastThreadLocalAdapter<T> implements ThreadLocalApi<T> {

    private final FastThreadLocal<T> fastThreadLocal = new FastThreadLocal<>();

    @Override
    public void set(T value) {
        fastThreadLocal.set(value);
    }

    @Override
    public T get() {
        return fastThreadLocal.get();
    }

    @Override
    public void remove() {
        fastThreadLocal.remove();
    }
}
