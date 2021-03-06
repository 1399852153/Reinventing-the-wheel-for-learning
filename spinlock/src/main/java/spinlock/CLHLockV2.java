package spinlock;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author xiongyx
 * @date 2021/7/29
 */
public class CLHLockV2 implements SpinLock{

    private static class CLHNode {
        private volatile CLHNode prev;
        private volatile boolean isLocked;

        public CLHNode() {
        }

        public CLHNode(CLHNode prev, boolean isLocked) {
            this.prev = prev;
            this.isLocked = isLocked;
        }
    }

    private static final CLHNode DUMMY_NODE = new CLHNode(null,false);

    private final CLHNode head;
    private final AtomicReference<CLHNode> tail;
    private final ThreadLocal<CLHNode> curNode;

    public CLHLockV2() {
        head = DUMMY_NODE;
        tail = new AtomicReference<>(DUMMY_NODE);
        curNode = ThreadLocal.withInitial(CLHNode::new);
    }

    @Override
    public void lock() {
        CLHNode currentNode = curNode.get();
        currentNode.isLocked = true;

        // cas的设置为当前tail为新的tail节点
        currentNode.prev = tail.getAndSet(currentNode);

        while(true){
            while(currentNode.prev.isLocked){
            }

            // 内层while循环结束，说明前驱节点已经释放了锁
            CLHNode prevNode = currentNode.prev;
            if(prevNode == head){
                // 如果前驱节点为head（Dummy节点）
                return;
            }else{
                currentNode.prev = prevNode.prev;
            }
        }
    }

    /**
     * 加锁（timeout毫秒内未成功加锁，则主动退出加锁）
     * */
    public boolean lock(long timeout) {
        CLHNode currentNode = curNode.get();
        currentNode.isLocked = true;

        // cas的设置为当前tail为新的tail节点
        currentNode.prev = tail.getAndSet(currentNode);

        final long deadline = System.currentTimeMillis() + timeout;

        while(true){
            while(currentNode.prev.isLocked){
                timeout = deadline - System.currentTimeMillis();
                if (timeout <= 0L) {
                    // 加锁超时，退出加锁
                    unlock();
                    // 当前已经超时，加锁失败返回false
                    return false;
                }
            }

            // 内层while循环结束，说明前驱节点已经释放了锁
            CLHNode prevNode = currentNode.prev;
            if(prevNode == head){
                // 如果前驱节点为head（Dummy节点）
                return true;
            }else{
                currentNode.prev = prevNode.prev;
            }
        }
    }

    @Override
    public void unlock() {
        CLHNode currentNode = curNode.get();
        curNode.remove();
        currentNode.isLocked = false;
    }
}
