package spinlock;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author xiongyx
 * @date 2021/7/24
 */
public class MCSLock implements SpinLock{

    private static class MCSNode {
        /**
         * 获取到锁的线程其后继争用锁的节点会持续不断的查询isLocked的值
         * 使用volatile修饰，使得释放锁修改isLocked时不会出现线程间变量不可见的问题
         * */
        private volatile boolean isLocked;

        private MCSNode next;
    }

    private final AtomicReference<MCSNode> tailNode;
    private final ThreadLocal<MCSNode> curNode;

    public MCSLock() {
        // MCS锁的tailNode初始化时为空，代表初始化时没有任何线程持有锁
        tailNode = new AtomicReference<>();
        // 设置threadLocal的初始化方法
        curNode = ThreadLocal.withInitial(MCSNode::new);
    }

    @Override
    public void lock() {
        MCSNode currNode = curNode.get();
        currNode.isLocked = true;

        MCSNode preNode = tailNode.getAndSet(currNode);
        if(preNode == null){
            // 当前线程加锁之前并不存在tail节点，则代表当前线程为最新的节点，直接认为是加锁成功
            currNode.isLocked = false;
        }else{
            // 之前的节点存在，令前驱节点next指向当前节点，以便后续前驱节点释放锁时能够找到currNode
            // 前驱节点释放锁时，会主动的更新currNode.isLocked（令currNode.isLocked=false）
            preNode.next = currNode;

            while (currNode.isLocked) {
                // 自旋等待当前节点自己的isLocked变为false
            }
        }
    }

    @Override
    public void unlock() {
        MCSNode currNode = curNode.get();
        if (currNode == null || currNode.isLocked) {
            // 前置防御性校验，如果当前线程节点为空或者当前线程自身没有成功获得锁，则直接返回，加锁失败
            return;
        }

        if(currNode.next == null){
            // 当前节点的next为空，说明其是MCS的最后一个节点
            // 以cas的形式将tailNode设置为null（防止此时有线程并发加锁 => lock方法中的tailNode.getAndSet()）
            boolean casSuccess = tailNode.compareAndSet(currNode,null);
            if(casSuccess){
                // 如果cas设置tailNode成功为null成功，则释放锁结束
                return;
            }else{
                // 如果cas设置失败，说明此时又有了新的线程节点入队了
                while (currNode.next == null) {
                    // 自旋等待，并发lock的线程执行（preNode.next = currNode），设置currNode的next引用
                }
            }
        }

        // 如果currNode.next存在，按照约定则释放锁时需要将其next的isLocked修改，令next节点线程结束自旋从而获得锁
        currNode.next.isLocked = false;
        // 方便GC，断开next引用
        currNode.next = null;
    }
}
