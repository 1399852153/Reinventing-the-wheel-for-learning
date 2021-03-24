package blockingqueue;

/**
 * @author xiongyx
 *
 * 阻塞队列
 * 1. 首先是一个先进先出的队列
 * 2. 提供特别的api，在入队时如果队列已满令当前操作线程阻塞；在出队时如果队列为空令当前操作线程阻塞
 * 3. 单个元素的插入、删除操作是线程安全的
 */
public interface MyBlockingQueue<E> {

    /**
     * 插入特定元素e，加入队尾
     * 非阻塞，立即返回结果
     * @return 返回true代表插入成功，返回false代表此前队列已满插入失败
     * */
    boolean offer(E e);

    /**
     * 队列头部的元素出队(返回头部元素，将其从队列中删除)
     * 非阻塞，立即返回结果
     * @return 返回头部元素，队列为空则返回null
     * */
    E poll();

    /**
     * 插入特定元素e，加入队尾
     * 队列已满时阻塞当前线程，直到队列中元素被其它线程删除并插入成功
     * */
    void put(E e) throws InterruptedException;

    /**
     * 队列头部的元素出队(返回头部元素，将其从队列中删除)
     * 队列为空时阻塞当前线程，直到队列被其它元素插入新元素并出队成功
     * */
    E take() throws InterruptedException;
}
