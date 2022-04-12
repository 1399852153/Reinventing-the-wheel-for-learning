package future;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.*;

public class MyFutureTask<V> implements MyFuture<V>,Runnable{

    private volatile int state;
    private static final int NEW = 0;
    private Callable<V> callable;


    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;

    static {
        try {
            Field getUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            getUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) getUnsafe.get(null);

            stateOffset = UNSAFE.objectFieldOffset(FutureTask.class.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset(FutureTask.class.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset(FutureTask.class.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public MyFutureTask(Callable<V> callable) {
        if (callable == null) {
            throw new NullPointerException();
        }

        this.callable = callable;
        this.state = NEW;
    }

    public MyFutureTask(Runnable runnable, V result) {
        // 使用适配器模式，将runnable包装成callable
        // 会执行runnable.run(),并且返回值固定为result（无法自由控制返回值的内容，适用性不如Callable类型的构造方法）
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;
    }

    @Override
    public void run() {
        // todo 防止runAndReset并发执行
//        if(state != NEW){
//            // 线程已经启动了，不能再执行了
//            return;
//        }
//
//        if (!UNSAFE.compareAndSwapObject(this, runnerOffset,null, Thread.currentThread())) {
//            return;
//        }

        Callable<V> c = callable;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return null;
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return null;
    }
}
