package threadlocal;

import threadlocal.api.ThreadLocalApi;

public class JDKThreadLocalAdapter<T> implements ThreadLocalApi<T> {

    private final ThreadLocal<T> threadLocal = new ThreadLocal<>();

    @Override
    public void set(T value) {
        threadLocal.set(value);
    }

    @Override
    public T get() {
        return threadLocal.get();
    }

    @Override
    public void remove() {
        threadLocal.remove();
    }
}
