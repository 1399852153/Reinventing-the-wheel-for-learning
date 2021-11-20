package aqs.v4;

/**
 * @author xiongyx
 * @date 2021/11/20
 *
 * boolean await(long time, TimeUnit unit)；
 * boolean awaitUntil(Date deadline) throws InterruptedException;
 * 这两个方法和awaitNanos实现差别不大，具体细节可以去jdk的源码中看，为了保证代码量的精简就不再支持了
 */
public interface Condition {

    void await() throws InterruptedException;

    void awaitUninterruptibly();

    long awaitNanos(long nanosTimeout) throws InterruptedException;

    void signal();

    void signalAll();
}
