package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 时间轮环形数组下标对应的桶(保存一个超时任务MyTimeoutTaskNode的链表)
 * */
public class MyHierarchyHashedTimeWheelBucketV2 implements Delayed {

    /**
     * 过期时间的绝对值
     */
    private final AtomicLong expiration = new AtomicLong(-1L);

    private final LinkedList<MyTimeoutTaskNode> linkedList = new LinkedList<>();

    public void addTimeout(MyTimeoutTaskNode timeout) {
        linkedList.add(timeout);
    }

    /**
     * 遍历链表中的所有任务，round全部减一，如果减为负数了则说明这个任务超时到期了，将其从链表中移除后并交给线程池执行指定的任务
     * */
    public void expireTimeoutTask(Executor executor){
        Iterator<MyTimeoutTaskNode> iterator = linkedList.iterator();
        while(iterator.hasNext()){
            MyTimeoutTaskNode currentNode = iterator.next();
            long currentNodeRound = currentNode.getRounds();
            if(currentNodeRound <= 0){
                // 将其从链表中移除
                iterator.remove();
                // count小于等于0，说明超时了，交给线程池去异步执行
                executor.execute(currentNode.getTargetTask());
            }else{
                // 当前节点还未超时，round自减1
                currentNode.setRounds(currentNodeRound-1);
            }

            // 简单起见，不考虑任务被外部自己取消的case(netty里的timeout.isCancelled())
        }
    }

    /**
     * 将当前bucket中的数据，通过flushInLowerWheelFn，全部转移到更底层的时间轮中
     * */
    public void flush(Consumer<MyTimeoutTaskNode> flushInLowerWheelFn){
        Iterator<MyTimeoutTaskNode> iterator = linkedList.iterator();
        while(iterator.hasNext()){
            MyTimeoutTaskNode currentNode = iterator.next();
            // 先从链表中移除
            iterator.remove();
            // 通过flushInLowerWheelFn，转移到更底层的时间轮中
            flushInLowerWheelFn.accept(currentNode);

            // 简单起见，不考虑任务被外部自己取消的case(netty里的timeout.isCancelled())
        }
    }

    /**
     * 设置过期时间
     * @return true 之前的expiration和参数不一致，说明是新的一轮，需要将当前bucket放入timer的延迟队列中
     *         false 之前的expiration和参数一致，说明是同一轮，无需任何操作
     */
    public boolean setExpiration(long expire) {
        // 如果getAndSet返回的之前的expiration和参数不一致，则说明已经是新的一轮了
        return expiration.getAndSet(expire) != expire;
    }

    public long getExpiration(){
        return expiration.get();
    }

    @Override
    public long getDelay(TimeUnit unit) {
        // expiration相当于绝对时间超时时间，是System.nanoTime() + 传入的delay参数计算出来的
        // 所以获得getDelay时需要再减掉才能保证在正确的时间点出队
        return Math.max(0,unit.convert(expiration.get() - System.nanoTime(), TimeUnit.NANOSECONDS));
    }

    @Override
    public int compareTo(Delayed o) {
        if (o instanceof MyHierarchyHashedTimeWheelBucketV2) {
            // 延迟时间越小的，排在越前面，越先被计时器获得
            return Long.compare(expiration.get(), ((MyHierarchyHashedTimeWheelBucketV2) o).expiration.get());
        }

        return 0;
    }
}
