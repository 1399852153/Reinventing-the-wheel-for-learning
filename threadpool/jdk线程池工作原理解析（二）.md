# jdk线程池工作原理解析(二)
本篇博客是jdk线程池ThreadPoolExecutor工作原理解析系列博客的第二篇，在第一篇博客中基于v1版本的MyThreadPoolExecutor从源码层面解析了ThreadPoolExecutor在RUNNING状态下处理任务的核心逻辑，
而在这篇博客中将会展开介绍jdk线程池ThreadPoolExecutor优雅停止的原理。  
* [jdk线程池ThreadPoolExecutor工作原理解析（自己动手实现线程池）（一）](https://www.cnblogs.com/xiaoxiongcanguan/p/16879296.html)
## ThreadPoolExecutor优雅停止源码分析(自己动手实现线程池v2版本)
ThreadPoolExecutor为了实现优雅停止功能，为线程池设置了一个状态属性，其共有5种情况。
在第一篇博客中介绍过，AtomicInteger类型的变量ctl同时维护了两个业务属性当前活跃工作线程个数与线程池状态，ctl的高3位用于存放线程池状态。
### 线程池工作状态介绍
线程池工作状态是单调推进的，即从运行时->停止中->完全停止。共有以下五种情况
**todo 附状态流程图**
##### 1. RUNNING
RUNNING状态，代表着线程池处于正常运行的状态**（运行时）**。RUNNING状态的线程池能正常的接收并处理提交的任务  
在ThreadPoolExecutor初始化时通过对ctl赋予默认属性来设置（private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));）
RUNNING状态下线程池正常工作的原理已经在第一篇博客中详细的介绍过了，这里不再赘述。
##### 2. SHUTDOWN
SHUTDOWN状态，代表线程池处于停止对外服务的状态**（停止中）**。不再接收新提交的任务，但依然会将workQueue工作队列中积压的任务处理完。  
用户可以通过调用shutdown方法令线程池由RUNNING状态进入SHUTDOWN状态，shutdown方法的会在下文详细展开分析。
##### 3. STOP
STOP状态，代表线程池处于停止状态。不再接受新提交的任务**（停止中）**，同时也不再处理workQueue工作队列中积压的任务，当前还在处理任务的工作线程将收到interrupt中断通知  
用户可以通过调用shutdownNow方法令线程池由RUNNING或者SHUTDOWN状态进入STOP状态，shutdownNow方法会在下文详细展开分析。
##### 4. TIDYING
TIDYING状态，代表着线程池即将完全终止，正在做最后的收尾工作**（停止中）**。
在线程池中所有的工作线程都已经完全退出，且工作队列中的任务已经被清空时会由SHUTDOWN或STOP状态进入TIDYING状态。
##### 5. TERMINATED
TERMINATED状态，代表着线程池完全的关闭**（完全停止）**。
```java
public class MyThreadPoolExecutorV2 implements MyThreadPoolExecutor {
    /**
     * 当前线程池中存在的worker线程数量 + 状态的一个聚合（通过一个原子int进行cas，来避免对两个业务属性字段加锁来保证一致性）
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;

    /**
     * 32位的有符号整数，有3位是用来存放线程池状态的，所以用来维护当前工作线程个数的部分就只能用29位了
     * 被占去的3位中，有1位原来的符号位，2位是原来的数值位
     * */
    private static final int CAPACITY = (1 << COUNT_BITS) - 1;

    /**
     * 线程池状态poolStatus常量（状态值只会由小到大，单调递增）
     * 线程池状态迁移图：
     *         ↗ SHUTDOWN ↘
     * RUNNING       ↓       TIDYING → TERMINATED
     *         ↘   STOP   ↗
     * 1 RUNNING状态，代表着线程池处于正常运行的状态。能正常的接收并处理提交的任务
     * 线程池对象初始化时，状态为RUNNING
     * 对应逻辑：private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
     *
     * 2 SHUTDOWN状态，代表线程池处于停止对外服务的状态。不再接收新提交的任务，但依然会将workQueue工作队列中积压的任务处理完
     * 调用了shutdown方法时，状态由RUNNING -> SHUTDOWN
     * 对应逻辑：shutdown方法中的advanceRunState(SHUTDOWN);
     *
     * 3 STOP状态，代表线程池处于停止状态。不再接受新提交的任务，同时也不再处理workQueue工作队列中积压的任务，当前还在处理任务的工作线程将收到interrupt中断通知
     * 之前未调用shutdown方法，直接调用了shutdownNow方法，状态由RUNNING -> STOP
     * 之前先调用了shutdown方法，后调用了shutdownNow方法，状态由SHUTDOWN -> STOP
     * 对应逻辑：shutdownNow方法中的advanceRunState(STOP);
     *
     * 4 TIDYING状态，代表着线程池即将完全终止，正在做最后的收尾工作
     * 当前线程池状态为SHUTDOWN,任务被消费完工作队列workQueue为空，且工作线程全部退出完成工作线程集合workers为空时，tryTerminate方法中将状态由SHUTDOWN->TIDYING
     * 当前线程池状态为STOP,工作线程全部退出完成工作线程集合workers为空时，tryTerminate方法中将状态由STOP->TIDYING
     * 对应逻辑：tryTerminate方法中的ctl.compareAndSet(c, ctlOf(TIDYING, 0)
     *
     * 5 TERMINATED状态，代表着线程池完全的关闭。之前线程池已经处于TIDYING状态，且调用的钩子函数terminated已返回
     * 当前线程池状态为TIDYING，调用的钩子函数terminated已返回
     * 对应逻辑：tryTerminate方法中的ctl.set(ctlOf(TERMINATED, 0));
     * */

    //  11100000 00000000 00000000 00000000
    private static final int RUNNING = -1 << COUNT_BITS;
    //  00000000 00000000 00000000 00000000
    private static final int SHUTDOWN = 0 << COUNT_BITS;
    //  00100000 00000000 00000000 00000000
    private static final int STOP = 1 << COUNT_BITS;
    //  01000000 00000000 00000000 00000000
    private static final int TIDYING = 2 << COUNT_BITS;
    //  01100000 00000000 00000000 00000000
    private static final int TERMINATED = 3 << COUNT_BITS;

    private static int runStateOf(int c) {
        return c & ~CAPACITY;
    }
    
    private static int ctlOf(int rs, int wc) {
        return rs | wc;
    }
    
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }
}    
```
因为线程池状态不是单独存放，而是放在ctl这一32位数据的高3位的，读写都比较麻烦，因此提供了runStateOf和ctlOf方法（位运算）来简化操作。  
线程池的状态是单调递推的，由于巧妙的设置了状态靠前的值会更小，因此可以直接比较状态的值来判断当前线程池状态是否推进到了指定的状态（runStateLessThan、runStateAtLeast、isRunning）。
## jdk线程池ThreadPoolExecutor优雅停止具体实现原理
线程池的优雅停止一般要能做到以下几点：
1. 线程池在中止后不能再受理新的任务
2. 线程池中止的过程中，已经提交的现存任务不能丢失（等待剩余任务执行完再关闭或者能够把剩余的任务吐出来还给用户）
3. 线程池最终关闭前，确保创建的所有工作线程都已退出，不会出现资源的泄露

### 如何中止线程池(shutdown shutdownNow)

### 如何保证线程池在中止后不能再受理新的任务

### 如何保证中止过程中不丢失已提交的任务
1. 等待剩余任务执行完再关闭
2. 把剩余的任务吐出来还给用户

### 如何保证线程池最终关闭前，所有工作线程都已退出

## 总结

