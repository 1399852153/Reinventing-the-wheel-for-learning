package aqs.v3;

import aqs.MyAqs;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.locks.LockSupport;

/**
 * @author xiongyx
 * @date 2021/5/22
 *
 * 自己实现的aqs，v3版本
 * 1.支持互斥模式、共享模式
 * 2.支持被阻塞线程发生被中断或加锁超时而取消排队
 */
public abstract class MyAqsV3 implements MyAqs {

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
            // 由于提供给cas内存中字段偏移量的unsafe类只能在被jdk信任的类中直接使用，这里使用反射来绕过这一限制
            Field getUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            getUnsafe.setAccessible(true);
            unsafe = (Unsafe) getUnsafe.get(null);

            stateOffset = unsafe.objectFieldOffset(MyAqsV3.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset(MyAqsV3.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset(MyAqsV3.class.getDeclaredField("tail"));

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
                Thread.currentThread().interrupt();
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
                            Thread.currentThread().interrupt();
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
         * */
        static final int CANCELLED =  1;
        static final int SIGNAL    = -1;
        static final int PROPAGATE = -3;


        volatile Node prev;
        volatile Node next;
        volatile Thread thread;
        volatile int waitStatus;

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
