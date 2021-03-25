package blockingqueue.array;

import blockingqueue.MyBlockingQueue;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xiongyx
 *@date 2021/3/23
 *
 * 数组作为底层结构的阻塞队列 v2版本
 */
public class MyArrayBlockingQueueV2<E> implements MyBlockingQueue<E> {

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
    private int count;

    private final ReentrantLock reentrantLock;

    private final Condition condition;

    //=================================================构造方法======================================================
    /**
     * 默认构造方法
     * */
    public MyArrayBlockingQueueV2() {
       this(DEFAULT_CAPACITY);
    }

    /**
     * 默认构造方法
     * */
    public MyArrayBlockingQueueV2(int initCapacity) {
        assert initCapacity > 0;

        // 设置数组大小为默认
        this.elements = new Object[initCapacity];

        // 初始化队列 头部,尾部下标
        this.head = 0;
        this.tail = 0;

        this.reentrantLock = new ReentrantLock();
        this.condition = this.reentrantLock.newCondition();
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

        this.count++;
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

        this.count--;

        return dataNeedRemove;
    }


    @Override
    public boolean offer(E e) {
        if(this.head == this.tail){
            // 队列已满
            return false;
        }else{
            // 队列未满，入队
            enqueue(e);
            return true;
        }
    }

    @Override
    public E poll() {
        return dequeue();
    }

    @Override
    public void put(E e) throws InterruptedException {
        // 先尝试获得互斥锁，以进入临界区
        reentrantLock.lockInterruptibly();
        try {
            // 因为被消费者唤醒后可能会被其它的生产者再度填满队列，需要循环的判断
            while (this.count == elements.length) {
                // put操作时，如果队列已满则进入条件变量的等待队列，并释放条件变量对应的锁
                condition.await();
            }
            // 走到这里，说明当前队列不满，可以执行入队操作
            enqueue(e);

            // 唤醒可能等待着的消费者线程
            // 由于共用了一个condition，所以不能用signal，否则一旦唤醒的也是生产者线程就会陷入上面的while死循环）
            condition.signalAll();
        } finally {
            // 入队完毕，释放锁
            reentrantLock.unlock();
        }
    }

    @Override
    public E take() throws InterruptedException {
        // 先尝试获得互斥锁，以进入临界区
        reentrantLock.lockInterruptibly();

        try {
            // 因为被生产者唤醒后可能会被其它的消费者消费而使得队列再次为空，需要循环的判断
            while(this.count == 0){
                condition.await();
            }

            E headElement = dequeue();

            // 唤醒可能等待着的生产者线程
            // 由于共用了一个condition，所以不能用signal，否则一旦唤醒的也是消费者线程就会陷入上面的while死循环）
            condition.signalAll();

            return headElement;
        } finally {
            // 出队完毕，释放锁
            reentrantLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        return this.count == 0;
    }
}
