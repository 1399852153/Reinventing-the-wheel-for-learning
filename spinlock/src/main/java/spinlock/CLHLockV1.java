package spinlock;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author xiongyx
 * @date 2021/7/22
 */
public class CLHLockV1 implements SpinLock{
    private static class CLHNode {
        /**
         * 获取到锁的线程其后继争用锁的节点会持续不断的查询isLocked的值
         * 使用volatile修饰，使得释放锁修改isLocked时不会出现线程间变量不可见的问题
         * */
        private volatile boolean isLocked;
    }

    private final AtomicReference<CLHNode> tailNode;
    private final ThreadLocal<CLHNode> curNode;

    // CLHLock构造函数，用于新建CLH锁节点时做一些初始化逻辑
    public CLHLockV1() {
        // 初始化时尾结点指向一个空的CLH节点
        tailNode = new AtomicReference<>(new CLHNode());
        // 设置threadLocal的初始化方法
        curNode = ThreadLocal.withInitial(CLHNode::new);
    }

    public void lock() {
        CLHNode currNode = curNode.get();
        currNode.isLocked = true;

        CLHNode preNode = tailNode.getAndSet(currNode);
        while (preNode.isLocked) {
            // 无限循环，等待获得锁
        }
    }

    @Override
    public void unlock() {
        CLHNode node = curNode.get();
        node.isLocked = false;

        // 清除当前threadLocal中的节点，避免再次Lock加锁时获取到之前的节点
        curNode.remove();
    }


}
