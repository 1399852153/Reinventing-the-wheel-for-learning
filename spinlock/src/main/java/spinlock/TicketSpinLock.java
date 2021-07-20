package spinlock;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author xiongyx
 * @date 2021/7/20
 */
public class TicketSpinLock implements SpinLock{

    /**
     * 排队号发号器
     * */
    private AtomicInteger ticketNum = new AtomicInteger();

    /**
     * 当前服务号
     * */
    private AtomicInteger currentServerNum = new AtomicInteger();


    public void lock() {
        // 首先原子性地获得一个排队号
        int myTicketNum = ticketNum.getAndIncrement();

        // 当前服务号与自己持有的服务号不匹配
        // 一直无限轮训，直到排队号与自己的服务号一致（等待排队排到自己）
        while (currentServerNum.get() != myTicketNum) {
        }
    }

    public void unlock() {
        // 释放锁时，代表当前服务已经结束
        // 当前服务号自增，使得拿到下一个服务号的线程能够获得锁
        currentServerNum.incrementAndGet();
    }

}
