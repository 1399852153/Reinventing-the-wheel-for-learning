package spinlock;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author xiongyx
 * @date 2021/7/22
 *
 * 原始版CLH锁（无显示prev前驱节点引用，无法支持取消加锁等场景）
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

    public CLHLockV1() {
        // 初始化时尾结点指向一个空的CLH节点
        tailNode = new AtomicReference<>(new CLHNode());
        // 设置threadLocal的初始化方法
        curNode = ThreadLocal.withInitial(CLHNode::new);
    }

    @Override
    public void lock() {
        CLHNode currNode = curNode.get();
        currNode.isLocked = true;

        // cas的设置当前节点为tail尾节点，并且获取到设置前的老tail节点
        // 老的tail节点是当前加锁节点的前驱节点（隐式前驱节点），当前节点通过监听其isLocked状态来判断其是否已经解锁
        CLHNode preNode = tailNode.getAndSet(currNode);
        while (preNode.isLocked) {
            // 无限循环，等待获得锁
        }

        // 循环结束，说明其前驱已经释放了锁，当前线程加锁成功
    }

    @Override
    public void unlock() {
        CLHNode node = curNode.get();

        // 清除当前threadLocal中的节点，避免再次Lock加锁时获取到之前的节点
        curNode.remove();

        node.isLocked = false;
    }
}
