package blockingqueue.array.needfix;


import blockingqueue.MyBlockingQueue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xiongyx
 *@date 2021/3/23
 *
 * 数组作为底层结构的阻塞队列 v5版本待优化版本2
 * (注意：跑起来是有问题的 MyArrayBlockingQueueV5才是ok的)
 */
public class MyArrayBlockingQueueV5NeedOpt2<E> implements MyBlockingQueue<E> {

    /**
     * 队列默认的容量大小
     * */
    private static final int DEFAULT_CAPACITY = 16;

    /**
     * 承载队列元素的底层数组
     * */
    private final Object[] elements;

    /**
     * 当前头部元素的下标
     * */
    private int head;

    /**
     * 下一个元素插入时的下标
     * */
    private int tail;

    /**
     * 队列中元素个数
     * */
    private final AtomicInteger count = new AtomicInteger();

    private final ReentrantLock putLock;

    private final Condition notEmpty;

    private final ReentrantLock takeLock;

    private final Condition notFull;


    //=================================================构造方法======================================================
    /**
     * 默认构造方法
     * */
    public MyArrayBlockingQueueV5NeedOpt2() {
       this(DEFAULT_CAPACITY);
    }

    /**
     * 默认构造方法
     * */
    public MyArrayBlockingQueueV5NeedOpt2(int initCapacity) {
        assert initCapacity > 0;

        // 设置数组大小为默认
        this.elements = new Object[initCapacity];

        // 初始化队列 头部,尾部下标
        this.head = 0;
        this.tail = 0;

        this.takeLock = new ReentrantLock();
        this.notEmpty = this.takeLock.newCondition();

        this.putLock = new ReentrantLock();
        this.notFull = this.putLock.newCondition();
    }

    /**
     * 下标取模
     * */
    private int getMod(int logicIndex){
        int innerArrayLength = this.elements.length;

        // 由于队列下标逻辑上是循环的
        if(logicIndex < 0){
            // 当逻辑下标小于零时

            // 真实下标 = 逻辑下标 + 加上当前数组长度
            return logicIndex + innerArrayLength;
        } else if(logicIndex >= innerArrayLength){
            // 当逻辑下标大于数组长度时

            // 真实下标 = 逻辑下标 - 减去当前数组长度
            return logicIndex - innerArrayLength;
        } else {
            // 真实下标 = 逻辑下标
            return logicIndex;
        }
    }

    /**
     * 入队
     * */
    private void enqueue(E e){
        // 存放新插入的元素
        this.elements[this.tail] = e;
        // 尾部插入新元素后 tail下标后移一位
        this.tail = getMod(this.tail + 1);
    }

    /**
     * 出队
     * */
    private E dequeue(){
        // 暂存需要被删除的数据
        E dataNeedRemove = (E)this.elements[this.head];
        // 将当前头部元素引用释放
        this.elements[this.head] = null;
        // 头部下标 后移一位
        this.head = getMod(this.head + 1);

        return dataNeedRemove;
    }

    @Override
    public void put(E e) throws InterruptedException {
        int currentCount;
        // 先尝试获得互斥锁，以进入临界区
        putLock.lockInterruptibly();
        try {
            // 因为被消费者唤醒后可能会被其它的生产者再度填满队列，需要循环的判断
            while (count.get() == elements.length) {
                // put操作时，如果队列已满则进入notFull条件变量的等待队列，并释放条件变量对应的互斥锁
                notFull.await();
                // 消费者进行出队操作时
            }
            // 走到这里，说明当前队列不满，可以执行入队操作
            enqueue(e);

            currentCount = count.getAndIncrement();
        } finally {
            // 入队完毕，释放锁
            putLock.unlock();
        }

        // 如果插入之前队列为空，才唤醒等待弹出元素的线程
        // 为了防止死锁，不能在释放putLock之前获取takeLock
        if (currentCount == 0) {
            signalNotEmpty();
        }
    }

    @Override
    public E take() throws InterruptedException {
        E headElement;
        int currentCount;

        // 先尝试获得互斥锁，以进入临界区
        takeLock.lockInterruptibly();
        try {
            // 因为被生产者唤醒后可能会被其它的消费者消费而使得队列再次为空，需要循环的判断
            while(this.count.get() == 0){
                notEmpty.await();
            }

            headElement = dequeue();

            currentCount = this.count.getAndDecrement();
        } finally {
            // 出队完毕，释放锁
            takeLock.unlock();
        }

        // 只有在弹出之前队列已满的情况下才唤醒等待插入元素的线程
        // 为了防止死锁，不能在释放takeLock之前获取putLock
        if (currentCount == elements.length) {
            signalNotFull();
        }

        return headElement;

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

    @Override
    public boolean isEmpty() {
        return this.count.get() == 0;
    }
}
