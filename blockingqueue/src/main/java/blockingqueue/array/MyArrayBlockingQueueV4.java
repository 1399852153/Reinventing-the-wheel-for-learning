package blockingqueue.array;

import blockingqueue.MyBlockingQueue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 博客中最终优化版的阻塞队列
 * */
public class MyArrayBlockingQueueV4<E> implements MyBlockingQueue<E> {

    /** 存放元素的数组 */
    private final Object[] items;

    /** 弹出元素的位置 */
    private int takeIndex;

    /** 插入元素的位置 */
    private int putIndex;

    /** 队列中的元素总数 */
    private AtomicInteger count = new AtomicInteger(0);

    /** 插入锁 */
    private final ReentrantLock putLock = new ReentrantLock();

    /** 队列未满的条件变量 */
    private final Condition notFull = putLock.newCondition();

    /** 弹出锁 */
    private final ReentrantLock takeLock = new ReentrantLock();

    /** 队列非空的条件变量 */
    private final Condition notEmpty = takeLock.newCondition();

    /**
     * 指定队列大小的构造器
     *
     * @param capacity  队列大小
     */
    public MyArrayBlockingQueueV4(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        items = new Object[capacity];
    }

    /**
     * 入队操作
     *
     * @param e 待插入的对象
     */
    private void enqueue(Object e) {
        // 将对象e放入putIndex指向的位置
        items[putIndex] = e;

        // putIndex向后移一位，如果已到末尾则返回队列开头(位置0)
        if (++putIndex == items.length)
            putIndex = 0;
    }

    /**
     * 出队操作
     *
     * @return  被弹出的元素
     */
    private E dequeue() {
        // 取出takeIndex指向位置中的元素
        // 并将该位置清空
        Object e = items[takeIndex];
        items[takeIndex] = null;

        // takeIndex向后移一位，如果已到末尾则返回队列开头(位置0)
        if (++takeIndex == items.length)
            takeIndex = 0;

        // 返回之前代码中取出的元素e
        return (E) e;
    }

    /**
     * 将指定元素插入队列
     *
     * @param e 待插入的对象
     */
    @Override
    public void put(E e) throws InterruptedException {
        int c = -1;
        putLock.lockInterruptibly();
        try {
            while (count.get() == items.length) {
                // 队列已满时进入休眠
                // 等待队列未满条件得到满足
                notFull.await();
            }

            // 执行入队操作，将对象e实际放入队列中
            enqueue(e);

            // 增加元素总数
            c = count.getAndIncrement();

            // 如果在插入后队列仍然没满，则唤醒其他等待插入的线程
            if (c + 1 < items.length)
                notFull.signal();

        } finally {
            putLock.unlock();
        }

        // 如果插入之前队列为空，才唤醒等待弹出元素的线程
        // 为了防止死锁，不能在释放putLock之前获取takeLock
        if (c == 0)
            signalNotEmpty();
    }

    /**
     * 唤醒等待队列非空条件的线程
     */
    private void signalNotEmpty() {
        // 为了唤醒等待队列非空条件的线程，需要先获取对应的takeLock
        takeLock.lock();
        try {
            // 唤醒一个等待非空条件的线程
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * 从队列中弹出一个元素
     *
     * @return  被弹出的元素
     */
    @Override
    public E take() throws InterruptedException {
        E e;
        int c = -1;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                // 队列为空时进入休眠
                // 等待队列非空条件得到满足
                notEmpty.await();
            }

            // 执行出队操作，将队列中的第一个元素弹出
            e = dequeue();

            // 减少元素总数
            c = count.getAndDecrement();

            // 如果队列在弹出一个元素后仍然非空，则唤醒其他等待队列非空的线程
            if (c - 1 > 0)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }

        // 只有在弹出之前队列已满的情况下才唤醒等待插入元素的线程
        // 为了防止死锁，不能在释放takeLock之前获取putLock
        if (c == items.length)
            signalNotFull();

        return e;
    }

    @Override
    public boolean isEmpty() {
        return this.count.get() == 0;
    }

    /**
     * 唤醒等待队列未满条件的线程
     */
    private void signalNotFull() {
        // 为了唤醒等待队列未满条件的线程，需要先获取对应的putLock
        putLock.lock();
        try {
            // 唤醒一个等待队列未满条件的线程
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

}
