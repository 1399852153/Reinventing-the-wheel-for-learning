package spinlock;

/**
 * @author xiongyx
 * @date 2021/7/18
 */
public interface SpinLock {

    /**
     * 加锁
     * */
    void lock();

    /**
     * 解锁
     * */
    void unlock();
}
