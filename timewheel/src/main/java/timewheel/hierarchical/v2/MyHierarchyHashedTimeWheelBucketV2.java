package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;
import timewheel.util.PrintDateUtil;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class MyHierarchyHashedTimeWheelBucketV2 implements Delayed {

    private final LinkedList<MyTimeoutTaskNode> taskList = new LinkedList<>();

    private final AtomicLong expiration = new AtomicLong(-1);

    public synchronized void addTimeout(MyTimeoutTaskNode timeout) {
        taskList.add(timeout);
    }

    public synchronized void flush(Consumer<MyTimeoutTaskNode> flush) {
        Iterator<MyTimeoutTaskNode> iterator = taskList.iterator();
        while (iterator.hasNext()){
            MyTimeoutTaskNode node = iterator.next();
            // 从当前bucket中移除，转移到更下层的时间轮中
            iterator.remove();
            flush.accept(node);

            // 简单起见，不考虑任务被外部自己取消的case(netty里的timeout.isCancelled())
        }

        this.expiration.set(-1L);
    }

    /**
     * 设置当前bucket的超时时间
     * @return 是否是一个新的bucket  true：是
     * */
    public boolean setExpiration(long expiration){
        long oldValue = this.expiration.getAndSet(expiration);

        // 如果不一样，说明当前的expiration已经超过了原来的expiration一圈了，逻辑上不再是同一个bucket
        return oldValue != expiration;
    }

    public long getExpiration(){
        return this.expiration.get();
    }

    @Override
    public long getDelay(TimeUnit unit) {
        // 还剩余多少时间过期
        long delayNanos = Math.max(this.expiration.get() - System.nanoTime(), 0);

        // 将纳秒单位基于unit转换
        return unit.convert(delayNanos,TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if(o instanceof MyHierarchyHashedTimeWheelBucketV2){
            return Long.compare(this.expiration.get(),((MyHierarchyHashedTimeWheelBucketV2) o).expiration.get());
        }

        return 0;
    }

    @Override
    public String toString() {
        return "expiration=" + PrintDateUtil.parseDate(this.expiration.get()) + " size=" + taskList.size() + " " + this.expiration.get();
    }
}
