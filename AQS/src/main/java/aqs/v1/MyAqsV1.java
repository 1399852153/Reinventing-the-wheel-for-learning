package aqs.v1;

import aqs.MyAqs;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.locks.LockSupport;

/**
 * @author xiongyx
 * @date 2021/5/22
 *
 * 自己实现的aqs，v1版本
 * 只支持互斥锁模式（无法处理被阻塞线程发生被中断）
 */
public abstract class MyAqsV1 implements MyAqs {

    private volatile int state;
    private transient volatile Node head;
    private transient volatile Node tail;
    private transient Thread exclusiveOwnerThread;

    private static final Unsafe unsafe;
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;

    static {
        try {
            Field getUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            getUnsafe.setAccessible(true);
            unsafe = (Unsafe) getUnsafe.get(null);

            stateOffset = unsafe.objectFieldOffset(MyAqsV1.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset(MyAqsV1.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset(MyAqsV1.class.getDeclaredField("tail"));
        } catch (Exception var1) {
            throw new Error(var1);
        }
    }

    /**
     * 设置独占当前aqs的线程
     * */
    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }
    /**
     * 获取独占当前aqs的线程
     * */
    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }

    protected final int getState() {
        return state;
    }
    protected final void setState(int newState) {
        state = newState;
    }
    protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    /**
     * 尝试加互斥锁，如果加锁失败则当前线程进入阻塞状态
     * */
    @Override
    public final boolean acquire(int arg) {
        // 尝试着去申请互斥锁
        boolean acquireResult = tryAcquire(arg);
        if(!acquireResult){
            // 申请互斥锁失败，新创建一个绑定当前线程的节点
            Node newWaiterNode = addWaiter();
            // 尝试着加入同步队列
            acquireQueued(newWaiterNode,arg);
        }

        // 不支持中断功能，返回false
        return false;
    }

    /**
     * 尝试着释放互斥锁
     * @return true：释放成功；false：释放失败
     * */
    @Override
    public final boolean release(int arg) {
        // 尝试着释放锁
        if (tryRelease(arg)) {
            // 成功释放
            Node h = this.head;
            if (h != null) {
                // 如果头节点存在，唤醒当前头节点的next节点对应的线程
                Node next = h.next;
                if(next != null){
                    LockSupport.unpark(next.thread);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public final boolean hasQueuedPredecessors() {
        Node t = tail;
        Node h = head;

        if(h == t){
            // tail=head两种情况，都为null说明队列为空；或者都指向同一个节点，队列长度为1
            // 说明此时队列中并没有其它等待锁的线程，返回false
            return false;
        }

        Node secondNode = h.next;
        if(secondNode != null){
            if(secondNode.thread == Thread.currentThread()){
                // 头节点存在后继节点，且就是当前线程，因此不需要排队
                return false;
            }else{
                // 头节点存在后继节点，但不是当前线程，因此需要排队
                return true;
            }
        }else{
            // tail != head，但是头节点却没有next节点，这是一种特殊的场景
            // 在enq入队操作的初始化队列操作时可能会出现，先通过compareAndSetHead设置了头节点，但是还没执行tail = head操作前的瞬间会出现
            // 此时，说明已经有一个别的线程正在执行入队操作，当前线程此时还未进行入队，进度更慢，所以还是需要去排队的
            return true;
        }
    }

    /**
     * 尝试着加入队列
     * */
    private void acquireQueued(final Node node, int arg) {
        for (; ; ) {
            final Node p = node.prev;
            // 如果是需要入队的节点是aqs头节点的next节点，则最后尝试一次tryAcquire获取锁
            if (p == head && tryAcquire(arg)) {
                // tryAcquire获取锁成功成功，说明此前的瞬间头节点对应的线程已经释放了锁
                // 令当前入队的节点成为aqs中新的head节点
                setHead(node);
                p.next = null; // help GC
                return;
            }else{
                // 阻塞当前线程
                LockSupport.park(this);
            }
        }
    }

    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    private Node addWaiter() {
        Node node = new Node(Thread.currentThread());
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            // 当队列不为空时，执行一次快速的入队操作（因为少了一次enq方法调用，会快一点？）
            if (compareAndSetTail(pred, node)) {
                // 快速入队成功，直接返回
                pred.next = node;
                return node;
            }
        }
        // 上面的快速入队操作失败了，使用enq循环cas直到入队（队列为空，利用enq方法初始化同步队列）
        enq(node);
        return node;
    }

    /**
     * 入队操作
     * 使用CAS操作+无限重试的方式来解决并发冲突的问题
     * @return 返回新的队尾节点
     * */
    private Node enq(final Node node) {
        for (;;) {
            Node currentTailNode = tail;
            if (currentTailNode == null) {
                // AQS的同步队列是惰性加载的，如果tail为null说明队列为空（head=null && tail=null）
                if (compareAndSetHead(new Node())) {
                    // 使用cas的方式先创建一个新节点，令tail和head同时指向这一新节点
                    // 并发时多个线程同时执行，只会有一个线程成功执行compareAndSetHead这一cas操作
                    tail = head;
                }
            } else {
                // 令当前入队节点node插入队尾
                node.prev = currentTailNode;
                // 使用cas的方式令aqs的tail指向node，使得新节点node成为新的队尾元素
                if (compareAndSetTail(currentTailNode, node)) {
                    // 并发时多个线程同时执行，获取到的tail引用值是一样的，只有最先执行compareAndSetTail的线程会成功
                    // compareAndSetTail执行成功后令tail引用指向了一个新的节点，因此同一时刻获取到相同tail引用的线程cas插入队尾的操作会失败（expect不对了）
                    currentTailNode.next = node;
                    return currentTailNode;
                }
                // compareAndSetTail执行失败的线程会进入新的循环，反复尝试compareAndSetTail的cas操作直到最终成功
            }
        }
    }

    /**
     * 尝试着去申请互斥锁（抽象方法，由具体的实现类控制）
     * */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }
    /**
     * 尝试着去释放互斥锁（抽象方法，由具体的实现类控制）
     * */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 内部同步队列的节点
     * */
    static final class Node {
        volatile Node prev;
        volatile Node next;
        volatile Thread thread;

        public Node() {
        }

        Node(Thread thread) {
            this.thread = thread;
        }
    }

    private boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }
    private boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }
}