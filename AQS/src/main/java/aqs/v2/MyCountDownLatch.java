package aqs.v2;

/**
 * @author xiongyx
 * @date 2021/5/27
 */
public class MyCountDownLatch {

    private static final class Sync extends MyAqsV2 {
        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            setState(count);
        }

        int getCount() {
            return getState();
        }

        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        protected boolean tryReleaseShared(int releases) {
            for (;;) {
                int c = getState();
                if (c == 0)
                    return false;
                int nextc = c-1;
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }
    }

    private final Sync sync;

    public MyCountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }

        this.sync = new Sync(count);
    }

    public void await() throws InterruptedException {
        this.sync.acquireShared(1);
    }

    public void countDown() {
        this.sync.releaseShared(1);
    }

}
