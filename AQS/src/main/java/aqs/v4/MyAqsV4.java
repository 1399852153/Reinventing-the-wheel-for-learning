package aqs.v4;

import aqs.MyAqs;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.locks.LockSupport;

/**
 * @author xiongyx
 * @date 2021/5/22
 *
 * 自己实现的aqs，v4版本（最终版）
 * 1.支持互斥模式、共享模式
 * 2.支持被阻塞线程发生被中断或加锁超时而取消排队
 * 3.支持条件变量（ConditionObject）
 */
public abstract class MyAqsV4 implements MyAqs {

    private volatile int state;
    private transient volatile Node head;
    private transient volatile Node tail;
    private transient Thread exclusiveOwnerThread;

    private static final Unsafe unsafe;
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;

    private static final long waitStatusOffset;
    private static final long nextOffset;

    static final long spinForTimeoutThreshold = 1000L;

    static {
        try {
            Field getUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            getUnsafe.setAccessible(true);
            unsafe = (Unsafe) getUnsafe.get(null);

            stateOffset = unsafe.objectFieldOffset(MyAqsV4.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset(MyAqsV4.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset(MyAqsV4.class.getDeclaredField("tail"));

            waitStatusOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("next"));

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
     * 尝试获取互斥锁，如果加锁失败则当前线程进入阻塞状态
     * */
    @Override
    public final boolean acquire(int arg) {
        // 尝试着去申请互斥锁
        boolean acquireResult = tryAcquire(arg);
        if(!acquireResult){
            // 申请互斥锁失败，新创建一个绑定当前线程的节点
            Node newWaiterNode = addWaiter(Node.EXCLUSIVE_MODE);

            boolean interrupted = acquireQueued(newWaiterNode,arg);
            if(interrupted){
                // 如果加锁过程中确实发生过了中断，但acquireQueued内部Thread.interrupted将中断标识清楚了
                // 在这里调用interrupt，重新补偿中断标识
                selfInterrupt();
            }
            // 尝试着加入同步队列
            return interrupted;
        }

        // 默认不是被中断打断的
        return false;
    }

    @Override
    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (!tryAcquire(arg)) {
            doAcquireInterruptibly(arg);
        }
    }

    @Override
    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        // Returns: true if acquired; false if timed out
        if(tryAcquire(arg)){
            return true;
        }else{
            return doAcquireNanos(arg,nanosTimeout);
        }
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
            if (h != null && h.waitStatus != 0) {
                // 如果头节点存在，唤醒当前头节点的next节点对应的线程
                unparkSuccessor(h);
            }
            return true;
        }
        return false;
    }

    @Override
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0) {
            doAcquireShared(arg);
        }
    }

    @Override
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (tryAcquireShared(arg) < 0) {
            doAcquireSharedInterruptibly(arg);
        }
    }

    @Override
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        if(tryAcquireShared(arg) >= 0){
            return true;
        }else{
            return doAcquireSharedNanos(arg, nanosTimeout);
        }
    }

    /**
     * 尝试着释放共享锁
     * @return true：释放成功；false：释放失败
     * */
    @Override
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
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
    private boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                final Node p = node.predecessor();
                // 如果是需要入队的节点是aqs头节点的next节点，则最后尝试一次tryAcquire获取锁
                if (p == head && tryAcquire(arg)) {
                    // tryAcquire获取锁成功成功，说明此前的瞬间头节点对应的线程已经释放了锁
                    // 令当前入队的节点成为aqs中新的head节点
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }else{
                    // 不是aqs头节点的next节点或者node是aqs头节点的next节点但tryAcquire获取锁失败了（非公平锁，被后入队的线程抢占了）
                    if (shouldParkAfterFailedAcquire(p, node)){
                        // 将当前线程阻塞
                        LockSupport.park(this);
                        if(Thread.interrupted()){
                            // 是被中断唤醒的,但是acquire=>acquireQueued是不可被中断的
                            // 意味着，即使被中断唤醒了，还是需要继续尝试着争用锁。
                            // 返回值interrupted为true标识着争用锁的过程中是发生了中断的
                            interrupted = true;
                        }
                    }
                }
            }
        }finally {
            if (failed) {
                // 发生异常导致入队失败
                cancelAcquire(node);
            }
        }
    }

    /**
     * 可被中断的获取互斥锁
     * */
    private void doAcquireInterruptibly(int arg) throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE_MODE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node)){
                    // 将当前线程阻塞
                    LockSupport.park(this);
                    // 被唤醒后检查是否是由于被中断而唤醒的
                    if(Thread.interrupted()){
                        // 是被中断唤醒的，抛出中断异常（这就是可被中断，因为被中断后会抛异常不再尝试入队了，可以和acquireQueued做比较）
                        // 同时finally中会将当前线程对应的Node设置为cancel
                        throw new InterruptedException();
                    }
                }
            }
        } finally {
            if (failed){
                cancelAcquire(node);
            }
        }
    }

    private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L) {
            // 超时时间不为正数，直接超时加锁失败
            return false;
        }

        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE_MODE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                // 尝试加锁失败
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    // 当前已经超时，加锁失败返回false
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
                    // 将当前线程阻塞nanosTimeout毫秒
                    LockSupport.parkNanos(this, nanosTimeout);
                    // 被唤醒有三种可能:
                    // 1. parkNanos超时时间已到被唤醒 => 在循环中尝试最后一次入队后（p == head && tryAcquire）,
                    // 如果失败则会进入的if（nanosTimeout <= 0L）分支，返回false
                    // 2. 被自己的前驱节点唤醒 => 尝试一次入队
                    // 3. 被中断唤醒，进入的if（Thread.interrupted()分支，抛出中断异常
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }


    private void doAcquireShared(int arg) {
        final Node node = addWaiter(Node.SHARED_MODE);
        boolean failed = true;

        try {
            boolean interrupted = false;

            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted) {
                            // Thread.interrupted()清楚了中断标识，如果interrupted为true说明此前已经发生过中断
                            // 重新补偿中断标识
                            selfInterrupt();
                        }
                        failed = false;
                        return;
                    }
                }

                if (shouldParkAfterFailedAcquire(p, node)){
                    // 将当前线程阻塞
                    LockSupport.park(this);
                    // 被唤醒后检查是否是由于被中断而唤醒的
                    if(Thread.interrupted()){
                        // 是被中断唤醒的
                        interrupted = true;
                    }
                }
            }
        }finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
        final Node node = addWaiter(Node.SHARED_MODE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node)){
                    // 将当前线程阻塞
                    LockSupport.park(this);
                    // 被唤醒后检查是否是由于被中断而唤醒的
                    if(Thread.interrupted()){
                        // 是被中断唤醒的，抛出中断异常（这就是可被中断，因为被中断后会抛异常不再尝试入队了，可以和acquireQueued做比较）
                        // 同时finally中会将当前线程对应的Node设置为cancel
                        throw new InterruptedException();
                    }
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L) {
            // 超时时间不为正数，直接超时加锁失败
            return false;
        }

        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED_MODE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }

                // 尝试加锁失败
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    // 当前已经超时，加锁失败返回false
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
                    // 将当前线程阻塞nanosTimeout毫秒
                    LockSupport.parkNanos(this, nanosTimeout);
                    // 被唤醒有三种可能:
                    // 1. parkNanos超时时间已到被唤醒 => 在循环中尝试最后一次入队后（p == head && tryAcquire）,
                    // 如果失败则会进入的if（nanosTimeout <= 0L）分支，返回false
                    // 2. 被自己的前驱节点唤醒 => 尝试一次入队
                    // 3. 被中断唤醒，进入的if（Thread.interrupted()分支，抛出中断异常
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head;
        setHead(node);

        if (propagate > 0 || h == null || h.waitStatus < 0 ||
                (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared()) {
                doReleaseShared();
            }
        }
    }

    private void doReleaseShared() {
        for (;;) {
            Node h = head;

            // 判断当前队列是否存在一个以上的节点
            if (h != null && h != tail) {

                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
                        // 可能头节点已经被其它被唤醒的线程并发的执行unparkSuccessor了，continue 重新执行循环检查状态
                        // 例如：存在读锁线程A、B、C、D，线程A先唤醒unparkSuccessor了B，然后B被唤醒后执行doReleaseShared时先唤醒了C也修改了头节点
                        // 则A线程h != head,重新执行循环，此时获得head.waitStatus为SIGNAL，但是被线程C又执行doReleaseShared并发的修改了其状态
                        continue;            // loop to recheck cases
                    }
                    // 唤醒后继节点中的线程（内部会将head.status设置为0）
                    unparkSuccessor(h);
                }
                else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
                    // 假设有读锁节点A->读锁节点B->读锁节点C，读锁节点A初始时为head节点，h=节点A
                    // 当前线程A唤醒的后继线程节点B在节点A执行到这里时已经先一步成为了head节点（线程A由于h != head而再次循环至此），
                    // 而线程B已经执行了unparkSuccessor并将自己的状态设置为了0（head = 节点B，head.waitStatus = 0），唤醒或者即将唤醒线程C
                    // 此时当前线程A执行至此时，需要将head的节点的waitStatus设置为PROPAGATE这一负数值，这样被唤醒的线程C如果加锁失败的话会执行shouldParkAfterFailedAcquire
                    // shouldParkAfterFailedAcquire中if(ws == 0)的else分支，再次将head节点的waitStatus设置为SIGNAL
                    continue;                // loop on failed CAS
                }
            }

            if (h == head) {
                // 如果没有发生并发的锁释放，h依然是head，说明被唤醒的后继节点执行tryAcquire失败或者是还没有来得及修改head
                break;
            }

            // 否则说明被唤醒的后继节点执行tryAcquire成功，且在这一并发瞬间修改了head，令head指向了自己（当前线程节点的后继节点）
            // 出现这种情况时，说明当前同步队列中可能存在大量被阻塞的读锁节点，当前线程会再次通过for循环尝试着去唤醒新head节点的后继，
            // 这比完全依赖当前新head节点挨个唤醒其后继速度要快很多（越来越多的节点将参与到唤醒当前新head后继的任务中，称为唤醒风暴）
            // 例如CountDownLatch中被阻塞了5000个线程（用作barrier屏障），调用countDown方法统一释放时，比起一个一个的唤醒线程，上述这种唤醒风暴的策略释放的速度会快很多
            // 当然也可能h == head判断成功，当前线程break退出后也不会影响读锁广播唤醒后继读锁节点的正确性，这只是性能上的优化
        }
    }

    /**
     * node对应线程取消排队，将参数node节点从同步队列中移除
     * */
    private void cancelAcquire(Node node) {
        if (node == null) {
            return;
        }

        node.thread = null;

        Node pred = node.prev;
        while (pred.waitStatus > 0) {
            // 循环往复，向前查询直到找到一个不处于CANCELLED状态的前驱节点
            node.prev = pred.prev;
            pred = pred.prev;
        }

        // 获得当前前驱节点的next节点（next节点引用在无锁cas的竞争条件下可能为null）
        Node predNext = pred.next;

        // 将当前节点状态设置为CANCELLED（由于CANCELLED是终态，所以没有使用compareAndSetWaitStatus，可以略微提高效率）
        node.waitStatus = Node.CANCELLED;

        // 如果当前节点是尾节点，cas的将自己的的前驱pred节点设置为新的尾节点
        // compareAndSetTail设置tail节点时，会和其它执行enq的线程产生冲突而失败（enq中的compareAndSetTail）
        if (node == tail && compareAndSetTail(node, pred)) {
            // cas的设置前驱节点的next为null（因为前驱已经是队尾节点了，所以其next应该为null）
            // compareAndSetNext设置next引用时，会和其它执行enq的线程产生冲突而失败（enq中的currentTailNode.next = node）
            // 但即使失败了也是正常的（不用重试），因为新的队尾节点已经建立，当前的pred节点已经不是队尾节点了
            compareAndSetNext(pred, predNext, null);
        }else{
            // node不是尾节点；或者compareAndSetTail失败，pred也不是尾节点了

            // AQS中被阻塞线程是通过前驱节点的waitStatus为SIGNAL来控制其是否被唤醒的，在被阻塞前通过shouldParkAfterFailedAcquire将其设置为SIGNAL后进入阻塞态
            // 当前节点执行cancelAcquire令自己变成CANCELLED状态时，需要令自己的后继节点依然能够被正确的唤醒（令其前驱节点为SIGNAL）
            // ===============================
            // 因此，如果自己的前驱节点是一个有效的节点时（pred.waitStatus <= 0并且thread != null），head头节点的thread=null所以也不是有效节点
            // 便可以直接将自己的前驱节点与自己的后继节点建立联系（pred.next = node.next，优化unparkSuccessor的效率）,前提是自己的后继节点是一个有效的节点
            // 注意：虽然在这之前已经通过一个while循环找到了当时不处于CANCELLED状态的前驱节点，但是在执行到这里时可能前驱节点的状态变了（例如前驱线程被中断了，也执行了cancelAcquire）
            // ===============================
            // 如果自己的前驱节点不是一个有效的节点时，则需要将自己的后继节点唤醒，令其通过shouldParkAfterFailedAcquire重新找到自己的有效前驱节点
            // 也能够通过后继节点线程执行的shouldParkAfterFailedAcquire方法将同步队列中部分位于CANCELLED状态的节点及时的清除掉
            int ws;
            if (pred != head &&
                ((ws = pred.waitStatus) == Node.SIGNAL || (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                    pred.thread != null) {
                // 前驱节点是一个有效节点
                Node next = node.next;
                if (next != null && next.waitStatus <= 0) {
                    // 后继也是一个需要被唤醒的有效节点
                    // next不是CANCELLED状态，令pred.next = node.next
                    compareAndSetNext(pred, predNext, next);
                }
            } else {
                // 前驱节点不是一个有效节点（dummy节点）
                unparkSuccessor(node);
            }

            // 令next指向自己，断开next节点与自己的连结，便于GC
            // 执行了cancelAcquire操作的节点，会被其它线程在执行shouldParkAfterFailedAcquire时从队列中摘除掉
            node.next = node; // help GC
        }
    }

    private void unparkSuccessor(Node node) {
        /*
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         */
        int ws = node.waitStatus;
        if (ws < 0){
            // 当前节点如果状态不是终态CANCELLED或0，将状态设置为0，避免后续重复的被唤醒（node.waitStatus小于0说明其next需要被唤醒）
            compareAndSetWaitStatus(node, ws, 0);
        }

        /*
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         */

        // 先尝试通过next引用找到node的next节点
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            // 但由于其next节点可能处于CANCELLED状态或是由于cas的并发而不存在（cas同步队列中只有prev是准确的，一定存在的；next只是一个优化）
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev) {
                // 因此从队列的尾部开始根据prev引用向前寻找，直到找到一个不为CANCELLED状态的节点，即为node节点实际上的next节点
                if (t.waitStatus <= 0) {
                    s = t;
                }
            }
        }
        if (s != null) {
            // 如果找到了next后继节点，将其对应的线程唤醒
            LockSupport.unpark(s.thread);
        }
    }

    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL) {
            // 在当前节点对应线程park阻塞前，保证前驱节点的状态为SIGNAL
            // 确保node对应的线程在前驱节点释放锁时能最终能将其正确的唤醒
            return true;
        }

        if (ws > 0) {
            do {
                node.prev = pred.prev;
                pred = pred.prev;
                // waitStatus = CANCELLED（执行cancelAcquire导致的）
                // 前驱节点是取消状态，直到找到一个不是处于取消状态的节点（无论如何head节点永远不会是取消状态，所以一定能退出循环）
                // 在查找的过程中，将处于取消状态的节点从同步队列中断开（node.prev = pred.prev）
            } while (pred.waitStatus > 0);
            // 找到了一个不是CANCELLED的前驱节点，令其next指向当前节点node
            pred.next = node;
            // 在经过一系列prev和最终next的拓扑调整后，
            // 在方法执行前同步队列中位于"参数node"和"与其距离最近的前驱节点"中处于CANCELLED状态的节点已经全部被移出了队列
        } else {
            // 如果pred节点的waitStatus不是CANCELLED，也不是SIGNAL。需要将其设置为SIGNAL
            // 那么什么时候会是这种情况呢？
            // 1. 新节点插入队尾时，原有的队尾节点状态为初始化状态0，此时需要先将原有的队尾节点（pred）设置为SIGNAL，这也是最常见的情况
            // 2. 锁释放时unparkSuccessor内compareAndSetWaitStatus(node, ws, 0)
            // 3. 共享锁释放，doReleaseShared时head节点被设置为PROPAGATE
            //    在2、3两种情况下，都说明此时head节点对应的线程已经释放或是正在释放锁
            //    返回false后令调用处再次尝试tryAcquire或者tryAcquireShared加锁将有很大的可能加锁成功（for无限循环）
            //    但如果依然加锁失败，则会再次调用该方法在最前面的if (ws == Node.SIGNAL)处返回true后，使用LockSupport.park令当前线程进入阻塞态
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }

        return false;
    }

    private void setHead(Node node) {
        // setHead只会在tryAcquire/tryAcquireShared之后被调用，不会产生并发
        // 只有enq初始化队列时才会执行compareAndSetHead，所以也不会和enq中的compareAndSetHead并发执行
        // 因此这里为了效率，设置head引用时不使用cas的compareAndSetHead方法，而是直接赋值
        head = node;
        node.thread = null;
        node.prev = null;
    }

    private Node addWaiter(int mode) {
        Node node = new Node(mode,Thread.currentThread());
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
     * 尝试着去申请共享锁（抽象方法，由具体的实现类控制）
     * */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }
    /**
     * 尝试着去释放共享锁（抽象方法，由具体的实现类控制）
     * */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * 内部同步队列的节点
     * */
    static final class Node {
        /**
         * Node.mode常量 互斥/共享模式
         * */
        static final int EXCLUSIVE_MODE = 0;
        static final int SHARED_MODE = 1;

        /**
         * Node.waitStatus常量
         *
         * CANCELLED： 因为被中断、超时等原因而取消排队的节点
         * SIGNAL：当前节点的next节点正等待被唤醒
         * CONDITION：当前节点位于条件对象的等待队列中
         * */
        static final int CANCELLED =  1;
        static final int SIGNAL    = -1;
        static final int CONDITION = -2;
        static final int PROPAGATE = -3;


        volatile Node prev;
        volatile Node next;
        volatile Thread thread;
        volatile int waitStatus;

        Node nextWaiter;

        int mode;

        Node(){
        }

        Node(int mode) {
            this.mode = mode;
        }

        Node(int mode,Thread thread) {
            this(mode);
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) {
            this.thread = thread;
            this.waitStatus = waitStatus;
        }

        boolean isShared(){
            return mode == SHARED_MODE;
        }

        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null) {
                throw new NullPointerException();
            }
            else {
                return p;
            }
        }
    }

    /**
     * 条件对象，内部含有一个无dummy节点的单向链表（等待队列）
     * */
    public class ConditionObject implements Condition, java.io.Serializable {
        /**
         * 等待队列头节点
         * */
        private transient Node firstWaiter;

        /**
         * 等待队列尾节点
         * */
        private transient Node lastWaiter;

        /**
         * 发生中断而退出等待时，不响应中断
         * */
        private static final int REINTERRUPT =  1;
        /**
         * 发生中断而退出等待时，抛出InterruptedException异常，直接退出等待以响应中断
         * */
        private static final int THROW_IE    = -1;

        /**
         * 将当前线程包装成等待队列的节点，并令其加入队尾
         * @return 返回插入的新节点
         */
        private Node addConditionWaiter() {
            Node oldTailNode = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            if (oldTailNode != null && oldTailNode.waitStatus != Node.CONDITION) {
                // 当前队列的队尾不是有效的CONDITION节点，进行一次全盘的清除，将非CONDITION节点从等待队列中剔除
                unlinkCancelledWaiters();
                oldTailNode = lastWaiter;
            }
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (oldTailNode == null) {
                // 插入时队列为空，令头节点指向新的节点
                firstWaiter = node;
            } else {
                // 插入时队列不为空，令之前队尾节点与新插入的节点建立联系（单向链表）
                oldTailNode.nextWaiter = node;
            }
            // 队尾节点引用指向新节点
            lastWaiter = node;
            return node;
        }

        /**
         * 对当前条件变量所属的等待队列，进行一次头节点到尾节点的全量遍历
         * 将状态不为Node.CONDITION的节点全部清除出队列（主要是位于CANCELLED状态的节点）
         * */
        private void unlinkCancelledWaiters() {
            // 从头节点开始遍历
            Node t = firstWaiter;
            // trail：轨迹
            // 遍历过程中，最近一次发现的状态为CONDITION的节点
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    // 当前节点不是CONDITION类型的节点
                    // 首先令当前节点与其后继节点断开连接
                    t.nextWaiter = null;
                    if (trail == null){
                        // 从头节点遍历到此，都没有发现任何一个CONDITION节点
                        // 令firstWaiter指向t.next节点，进行队列的收缩（头节点开始的N个连续的非CONDITION节点都会被踢出队列）
                        firstWaiter = next;
                    } else {
                        // 令trail.next指向当前非CONDITION类型节点的next，跳过当前节点
                        // 与前面的t.nextWaiter = null协作，将当前非CONDITION节点踢出队列（t的前驱存在有效的CONDITION，但自身需要被踢出队列）
                        trail.nextWaiter = next;
                    }
                    if (next == null) {
                        // 如果当前遍历的节点不存在后继了（遍历到队尾节点了）
                        // 令等待队列的尾节点指向trail，即最后一个有效的CONDITION节点
                        lastWaiter = trail;
                    }
                } else {
                    // trail指向最新发现的CONDITION类型节点
                    trail = t;
                }
                t = next; // 遍历队列节点
            }
        }

        /**
         * 检查当前节点线程的中断状态
         * 返回0：代表未发生中断
         * 返回THROW_IE：需要调用方抛出中断异常，退出等待
         * 返回REINTERRUPT：不抛出中断异常，通过selfInterrupt，恢复中断标志位
         */
        private int checkInterruptWhileWaiting(Node node) {
            if(Thread.interrupted()){
                // 发生了中断
                boolean result = transferAfterCancelledWait(node);
                if(result){
                    // 返回THROW_IE，代表cancel取消操作先于signal操作
                    return THROW_IE;
                }else{
                    // 返回REINTERRUPT，代表signal操作先于cancel取消操作
                    return REINTERRUPT;
                }
            }else{
                // 未发生中断
                return 0;
            }
        }

        /**
         * Transfers node, if necessary, to sync queue after a cancelled wait.
         * Returns true if thread was cancelled before being signalled.
         *
         * 返回true，说明CANCEL取消操作先于signal操作
         * @param node the node
         * @return true if cancelled before the node was signalled
         */
        final boolean transferAfterCancelledWait(Node node) {
            // CAS的将位于等待队列中node的状态由CONDITION设置为0（与transferForSignal中等价的CAS操作做互斥）
            if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
                // 中断cancel流程的CAS设置成功，说明此时没有发生并发的signal或者竞争成功了（CANCEL取消操作先于signal操作）
                // 将node转移至AQS的同步队列中
                enq(node);
                // 返回true代表CANCEL取消操作先于signal操作
                return true;
            }

            // 上述的CAS操作失败，说明signal操作先于cancel操作发生
            while (!isOnSyncQueue(node)) {
                // 无限循环，判断node是否已经被位于transferForSignal中的enq操作插入了同步队列
                // 如果没有插入，则需要一直以while自旋的方式阻塞在这里（因为enq的执行是很快速的，所以持续极短时间的自旋是可以接受的）
                // 但通过Thread.yield()，主动向操作系统请求让出CPU，可以一定程度上提高CPU的利用率
                Thread.yield();
            }
            // 返回false代表signal操作先于CANCEL取消操作
            return false;
        }

        /**
         * 将节点node从等待队列转移到AQS的同步队列中
         * @return
         * 返回true代表转移成功（signal先于cancel执行）
         * 返回false代表转移失败（cancal先于signal执行）
         */
        final boolean transferForSignal(Node node) {
            // CAS的将位于等待队列中node的状态由CONDITION设置为0（与transferAfterCancelledWait中等价的CAS操作做互斥）
            if (!compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
                // signal操作的CAS设置失败，说明CANCEL取消操作先于signal操作
                // 直接返回false
                return false;
            }

            /*
             * Splice onto queue and try to set waitStatus of predecessor to
             * indicate that thread is (probably) waiting. If cancelled or
             * attempt to set waitStatus fails, wake up to resync (in which
             * case the waitStatus can be transiently and harmlessly wrong).
             */

            // 令node加入同步队列队尾
            Node p = enq(node);
            // 获得入队后，node前驱节点的状态
            int ws = p.waitStatus;
            // 如果前驱节点的状态为已取消，或者CAS的设置其状态为SIGNAL失败
            if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL)) {
                // 将当前节点线程唤醒（因为前驱节点已经是cancel态了，需要主动唤醒去清除）
                LockSupport.unpark(node.thread);
            }
            return true;
        }

        /**
         * 根据参数interruptMode，进行处理
         * 1 中断模式为THROW_IE，代表抛出中断异常
         * 2 中断模式为REINTERRUPT，代表不抛出异常，仅仅是将消费过的中断标志位复原
         * 3 没有发生中断，什么也不做
         */
        private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {
            if (interruptMode == THROW_IE) {
                // 中断模式为THROW_IE，代表抛出中断异常
                throw new InterruptedException();
            } else if (interruptMode == REINTERRUPT) {
                // 中断模式为REINTERRUPT，代表不抛出异常，仅仅是将消费过的中断标志位复原
                selfInterrupt();
            }
            // 没有发生中断，什么也不做
        }

        /**
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                // 由于需要将排在最前面的节点从条件队列中移动到同步队列，因此需要将队列头部后移一位
                if ((firstWaiter = first.nextWaiter) == null) {
                    lastWaiter = null;
                }
                // 同时断开当前被移除节点的next链接（老的当前头节点）
                first.nextWaiter = null;
            } while (!transferForSignal(first) && (first = firstWaiter) != null);
            // while条件再度循环的条件为transferForSignal=false && firstWaiter != null
            // transferForSignal=false,说明当前头节点已经抢先被取消是CANCELLED状态了，所以需要再进行一次循环来唤醒下一个节点
            // firstWaiter != null则代表队列不为空
            // 综上可得再一次循环的条件为：被选中的节点（first）已经被取消了，transferForSignal失败；且当前等待队列还存在下一个节点可以被唤醒
        }

        /**
         * 将当前条件队列中的所有节点都转移至AQS同步队列中，清空当前条件队列
         */
        private void doSignalAll(Node first) {
            // 首先将头节点、尾节点置空，使得该条件队列无法再被访问
            lastWaiter = firstWaiter = null;
            do {
                // 从头节点开始循环往复的向后遍历，直到将每一个节点都通过transferForSignal转移至同步队列中
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * 实现条件变量的等待
         * 1 如果当前线程被中断，抛出InterruptedException异常
         * 2 通过getState的返回值state，来保存释放锁时的快照状态（fullyRelease）
         * 3 调用release方法时，参数传入getState时获得的快照值state进行所得释放（如果失败则回抛出IllegalMonitorStateException异常）
         * 4 当前线程被阻塞直到被signal操作唤醒，或者被中断唤醒
         * 5 被唤醒后，节点前往同步队列时acquire传入state，恢复被await挂起前的锁状态
         * 6 如果在第4步的时候，是被中断唤醒的（不是被signal唤醒），则抛出InterruptedException异常
         * */
        @Override
        public void await() throws InterruptedException {
            if (Thread.interrupted()) {
                // await前已经发生中断，直接抛出中断异常
                throw new InterruptedException();
            }
            // node从队尾加入队列
            Node node = addConditionWaiter();
            // 获取当前state快照，并将state清0用以释放互斥锁
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    // 返回值不为0说明发生了中断，break跳出当前while循环
                    break;
                }
                // 走到这里说明线程被唤醒了，但并不是因为发生中断被唤醒的，而是被signal操作唤醒的
            }

            // 被唤醒后，令节点工作在同步队列中（方法内tryAcquire加互斥锁失败则阻塞在同步队列中）
            boolean interrupted = acquireQueued(node, savedState);
            if (interrupted && interruptMode != THROW_IE) {
                // acquireQueued过程中有发生中断，但interruptMode为不抛出异常，则interruptMode修正为REINTERRUPT
                // 主要针对interruptMode=0，在上面checkInterruptWhileWaiting中没发生中断的场景
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) { // clean up if cancelled
                // node不是等待队列的尾节点，通过unlinkCancelledWaiters将自己从等待队列中剔除（不再是CONDITION节点了）
                unlinkCancelledWaiters();
            }
            // node如果是尾节点则会被暂时保留，等待新的后继节点在addConditionWaiter中调用unlinkCancelledWaiters将当前node剔除

            if (interruptMode != 0) {
                // interruptMode中断状态不为0，代表发生了中断，根据interruptMode来进行相应的处理
                reportInterruptAfterWait(interruptMode);
            }
        }

        /**
         * 不可被中断阻止的await(发生中断时，不响应中断，继续等待)
         * 只能通过signal来结束await等待
         * */
        @Override
        public void awaitUninterruptibly() {
            // node从队尾加入队列
            Node node = addConditionWaiter();
            // 获取当前state快照，并将state清0用以释放互斥锁
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                // 不再同步队列中，则将当前线程阻塞
                LockSupport.park(this);
                // 被唤醒后，判断是否是因为中断唤醒的
                if (Thread.interrupted()) {
                    // 是中断唤醒的，但是由于awaitUninterruptibly不响应中断
                    // 设置interrupted标记为true，但继续下一次循环
                    interrupted = true;
                }
            }

            // isOnSyncQueue为false，退出循环，说明是被signal操作唤醒
            // acquireQueued返回值为true 或者 interrupted为true都代表awaitUninterruptibly期间线程被中断唤醒过
            if (acquireQueued(node, savedState) || interrupted) {
                // 通过selfInterrupt恢复被方法内消耗的中断标示，令调用者感知到awaitUninterruptibly期间线程被中断唤醒过
                selfInterrupt();
            }
        }

        /**
         * 在await支持中断的基础上，支持超时取消
         * @param nanosTimeout 等待的最大超时时间
         * */
        @Override
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            if (Thread.interrupted()) {
                // await前已经发生中断，直接抛出中断异常
                throw new InterruptedException();
            }
            // node从队尾加入队列
            Node node = addConditionWaiter();
            // 获取当前state快照，并将state清0用以释放互斥锁
            int savedState = fullyRelease(node);
            // 通过系统当前时间+参数中指定的超时时间，算出超时的最终时间点
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    // 如果nanosTimeout小于等于0，说明已经超时则取消等待，并送入同步队列中
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold) {
                    // nanosTimeout超时时间大于预设的自旋超时时间，则令线程进入阻塞态
                    // 并且在nanosTimeout后被自动唤醒
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    // 如果发生了中断则interruptMode不为0，跳出循环
                    break;
                }
                // （nanosTimeout < spinForTimeoutThreshold）未被阻塞，修正nanosTimeout的值
                nanosTimeout = deadline - System.nanoTime();
            }

            // 被唤醒后，令节点工作在同步队列中（方法内tryAcquire加互斥锁失败则阻塞在同步队列中）
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                // acquireQueued过程中有发生中断，但interruptMode为不抛出异常，则interruptMode修正为REINTERRUPT
                // 主要针对interruptMode=0，在上面checkInterruptWhileWaiting中没发生中断的场景
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) {
                // node不是等待队列的尾节点，通过unlinkCancelledWaiters将自己从等待队列中剔除（不再是CONDITION节点了）
                unlinkCancelledWaiters();
            }
            if (interruptMode != 0) {
                // interruptMode中断状态不为0，代表发生了中断，根据interruptMode来进行相应的处理
                reportInterruptAfterWait(interruptMode);
            }

            // 执行完毕时，距离参数指定的nanosTimeout的剩余时间
            return deadline - System.nanoTime();
        }

        @Override
        public void signal() {
            if (!isHeldExclusively()) {
                // 没有拿到互斥锁调用signal方法，抛出异常
                throw new IllegalMonitorStateException();
            }
            Node first = firstWaiter;
            if (first != null) {
                // 等待队列不为空
                doSignal(first);
            }
        }

        @Override
        public void signalAll() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            Node first = firstWaiter;
            if (first != null) {
                doSignalAll(first);
            }
        }
    }

    /**
     * Invokes release with current state value; returns saved state.
     * Cancels node and throws exception on failure.
     * @param node the condition node for this wait
     * @return previous sync state
     *
     * 将当前线程获得的锁全量的释放
     * 区别于一般release时state-1的做法，无论state为多少（无论当前线程重入了多少次），都一并置为0来释放锁
     * 如果释放锁失败，会将当前节点设置为CANCELLED状态
     * @return 返回释放锁前的state快照值
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            // 获得当前state(当前已重入N次，则返回N)
            int savedState = getState();
            // release时一把全部释放，直接将state清零
            if (release(savedState)) {
                failed = false;
                // 返回释放锁时的state值，后续还原时用
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed) {
                // release释放锁失败，将当前条件队列节点设置为CANCELLED已取消
                node.waitStatus = Node.CANCELLED;
            }
        }
    }

    /**
     * 判断节点node是否位于AQS的同步队列中
     * */
    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null) {
            // CONDITION节点说明是在条件变量的等待队列中
            // 同步队列由于dummy头节点存在，因此prev不可能为null
            // 所以这两种情况都标示着节点不位于AQS的同步队列中
            return false;
        }
        if (node.next != null) // If has successor, it must be on queue
            // node.waitStatus != CONDITION
            // && node.prev != null && node.next != null
            // 不为CONDITION节点，且前驱节点/后继节点都存在，说明其位于同步队列中
            return true;

        // node.waitStatus != CONDITION && node.prev != null
        // enq时，已经与当时的同步队列tail节点建立了关联，但next还未存在（可能是最新的队尾节点，或者其后继节点还未与其建立next的关联）
        // 所以从当前的tail节点开始向前遍历，判断其是否是位于同步队列中（因为prev引用是cas保证可靠的）
        return findNodeFromTail(node);
    }

    /**
     * 从同步队列的tail末尾开始向前遍历，判断node是否位于同步队列中
     * */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (;;) {
            if (t == node) {
                return true;
            }
            if (t == null) {
                return false;
            }
            t = t.prev;
        }
    }

    /**
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }


    private boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }
    private boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    private static boolean compareAndSetWaitStatus(Node node, int expect, int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }
    private static boolean compareAndSetNext(Node node, Node expect, Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
