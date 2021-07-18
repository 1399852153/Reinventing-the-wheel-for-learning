package spinlock;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author xiongyx
 * @date 2021/7/18
 *
 * 原始的自旋锁，不支持重入
 */
public class OriginalSpinLock implements SpinLock{
    /**
     * 标识当前自旋锁的持有线程
     *
     * AtomicReference类型配合cas
     * */
    private final AtomicReference<Thread> lockOwner = new AtomicReference<>();

    @Override
    public void lock() {
        Thread currentThread = Thread.currentThread();

        // cas争用锁
        // 只有当加锁时之前lockOwner为null，才代表加锁成功,结束循环
        // 否则说明加锁时已经有其它线程获得了锁，无限循环重试
        while (!lockOwner.compareAndSet(null, currentThread)) {
        }

    }

    @Override
    public void unlock() {
        Thread currentThread = Thread.currentThread();

        // cas释放锁
        // 只有之前加锁成功的线程才能够将其重新cas的设置为null
        lockOwner.compareAndSet(currentThread, null);
    }
}
