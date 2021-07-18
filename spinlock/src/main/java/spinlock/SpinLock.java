package spinlock;

/**
 * @author xiongyx
 * @date 2021/7/18
 */
public interface SpinLock {

    void lock();

    void unlock();
}
