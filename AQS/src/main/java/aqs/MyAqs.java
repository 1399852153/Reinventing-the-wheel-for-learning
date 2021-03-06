package aqs;

/**
 * @author xiongyx
 * @date 2021/6/2
 *
 * 不同版本MyAqs对外提供的接口
 */
public interface MyAqs {

    /**
     * 尝试获取互斥锁（不可中断）
     * 如果加锁过程线程被中断，不会退出加锁过程，但会返回true
     * @return true加锁过程中发生了中断；false加锁过程中未发生中断
     * */
    default boolean acquire(int arg) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取互斥锁（可中断）
     * 如果加锁过程线程被中断，会立即退出aqs并抛出中断异常
     * */
    default void acquireInterruptibly(int arg) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取互斥锁，引入超时机制
     * @return true if acquired; false if timed out
     * */
    default boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    /**
     * 释放互斥锁
     * */
    default boolean release(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取共享锁
     * */
    default void acquireShared(int arg){
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取共享锁（可中断）
     * 如果加锁过程线程被中断，会立即退出aqs并抛出中断异常
     * */
    default void acquireSharedInterruptibly(int arg) throws InterruptedException{
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取共享锁，引入超时机制
     * @return true if acquired; false if timed out
     * */
    default boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    /**
     * 释放共享锁
     * */
    default boolean releaseShared(int arg){
        throw new UnsupportedOperationException();
    }


    /**
     * 判断当前aqs队列是否已经有至少一个线程处于等待状态，该方法主要用于实现公平锁/非公平锁
     * 在执行aqs的acquire入队时，会令新申请锁的线程进行一次征用锁的操作（acquireQueued方法中的tryAcquire）
     * 这使得新申请锁的线程能够和之前已经在同步队列中等待的线程一起竞争锁，新申请锁的线程有可能比更早申请锁的线程先获得锁（非公平机制）
     *
     * 非公平的锁机制由于利用了cas操作进行充分的竞争，其性能高于公平的锁机制（按照实际申请锁的顺序来得到锁，会产生更多的线程上下文切换），
     * 缺点是在高并发场景下先入队的线程容易陷入饥饿状态（前面先进入等待的线程迟迟拿不到锁）
     *
     * 使用aqs实现公平锁机制的最佳实践是在tryAcquire中使用hasQueuedPredecessors进行判断，
     * 如果返回ture，说明同步队列中还存在更早进入等待锁的线程，因此tryAcquire返回false，令当前线程进入队尾等待，不去抢先获得锁，以实现公平的特性
     *
     * @return false说明不需要排队，true说明需要排队
     * */
    boolean hasQueuedPredecessors();


}
