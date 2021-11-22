package blockingqueue.jdk;

import blockingqueue.MyBlockingQueue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author xiongyx
 * @date 2021/3/25
 */
public class JDKLinkedBlockingQueue<E> implements MyBlockingQueue<E> {

    private final BlockingQueue<E> jdkBlockingQueue;

    /**
     * 指定队列大小的构造器
     *
     * @param capacity  队列大小
     */
    public JDKLinkedBlockingQueue(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        jdkBlockingQueue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public void put(E e) throws InterruptedException {
        jdkBlockingQueue.put(e);
    }

    @Override
    public E take() throws InterruptedException {
        return jdkBlockingQueue.take();
    }

    @Override
    public boolean isEmpty() {
        return jdkBlockingQueue.isEmpty();
    }

    @Override
    public String toString() {
        return "JDKLinkedBlockingQueue{" +
                "jdkBlockingQueue=" + jdkBlockingQueue +
                '}';
    }
}
