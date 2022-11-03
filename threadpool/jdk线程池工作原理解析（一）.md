# jdk线程池工作原理解析(一)
## 线程池介绍
在日常开发中经常会遇到使用其它线程将大量任务异步处理的场景（异步化以及提升系统的吞吐量），但在使用线程时存在着两个痛点。
1. 在java等很多主流语言中每个逻辑上的线程底层都对应着一个系统线程（不考虑虚拟线程的情况）。操作系统创建一个新线程是存在一定开销的，
   在需要执行**大量的**异步任务时，如果处理每个任务时都直接向系统申请创建一个线程来执行，并在任务执行完毕后再回收线程，则创建/销毁大量线程的开销将无法忍受。
2. 每个系统线程都会占用一定的内存空间，且系统在调度不同线程上下文切换时存在一定的cpu开销。因此在一定的硬件条件下，操作系统能同时维护的系统线程个数是比较有限的。
   在使用线程的过程中如果没有控制好流量，会很容易创建过多的线程而耗尽系统资源，令系统变得不可用。

而线程池正是为解决上述痛点而生的，其通过两个手段来解决上述痛点。
### 池化线程资源
池化线程资源，顾名思义就是维护一个存活线程的集合（池子）。提交任务的用户程序不直接控制线程的创建和销毁，不用每次执行任务时都申请创建一个新线程，而是通过线程池间接的获得线程去处理异步任务。
线程池中的线程在执行完任务后通常也不会被系统回收掉，而是继续待在池子中用于执行其它的任务（执行堆积的待执行任务或是等待新任务）。  
**线程池通过池化线程资源，避免了系统反复创建/销毁线程的开销，大幅提高了处理大规模异步任务时的性能。**
### 对线程资源的申请进行收口，限制系统资源的使用
如果程序都统一使用线程池来处理异步任务，那么线程池内部便可以对系统资源的使用施加一定限制。
例如用户可以指定一个线程池最大可维护的线程数量，避免耗尽系统资源。
当用户提交任务的速率过大，导致线程池中的线程数到达指定的最大值时依然无法满足需求时，线程池可以通过丢弃部分任务或限制提交任务的流量的方式来处理这一问题。  
**线程池通过对线程资源的使用进行统一收口，用户可以通过线程池的参数限制系统资源的使用，从而避免系统资源耗尽。**

## jdk线程池ThreadPoolExecutor简单介绍
前面介绍了线程池的概念，而要深入理解线程池的工作原理最好的办法便是找到一个优秀的线程池实现来加以研究。  
而自jdk1.5中引入的通用线程池框架ThreadPoolExecutor便是一个很好的学习对象。其内部实现不算复杂，却在高效实现核心功能的同时还提供了较丰富的拓展能力。
#####
下面从整体上介绍一下jdk通用线程池ThreadPoolExecutor的工作原理（基于jdk8）。
### ThreadPoolExecutor运行时工作流程
首先ThreadPoolExecutor允许用户从两个不同维度来控制线程资源的使用，即最大核心线程数(corePoolSize)和最大线程数(maximumPoolSize)。
最大核心线程数：核心线程指的是**通常**常驻线程池的线程。常驻线程在线程池没有任务空闲时也不会被销毁，而是处于idle状态，这样在新任务到来时就能很快的进行响应。
最大线程数：和第一节中提到的一样，即线程池中所能允许的活跃线程的最大数量。
#####
在向ThreadPoolExecutor提交任务时（execute方法），会执行一系列的判断来决定任务应该如何被执行（源码在下一节中具体分析）。
1. 首先判断当前活跃的线程数是否小于指定的**最大核心线程数corePoolSize**。  
   如果为真，则说明当前线程池还未完成预热，核心线程数不饱和，创建一个新线程来执行该任务。  
   如果为假，则说明当前线程池已完成预热，进行下一步判断。
2. 尝试将当前任务放入**工作队列workQueue**（阻塞队列BlockingQueue类型），工作队列中的任务会被线程池中的活跃线程按入队顺序逐个消费。  
   如果入队成功，则说明当前工作队列未满，入队的任务将会被线程池中的某个活跃线程所消费并执行。  
   如果入队失败，则说明当前工作队列已饱和，线程池消费任务的速度可能太慢了，可能需要创建更多新线程来加速消费，进行下一步判断。
3. 判断当前活跃的线程数是否小于指定的**最大线程数maximumPoolSize**。  
   如果为真，则说明当前线程池所承载的线程数还未达到参数指定的上限，还有余量来创建新的线程加速消费，创建一个新线程来执行该任务。  
   如果为假，则说明当前线程池所承载的线程数达到了上限，但处理任务的速度依然不够快，需要触发**拒绝策略**。  
![ThreadPoolExecutor提交任务流程图.png](ThreadPoolExecutor提交任务流程图.png)
### ThreadPoolExecutor优雅停止
线程池的优雅停止一般要能做到以下几点：
1. 线程池在中止后不能再受理新的任务
2. 线程池中止的过程中，已经提交的现存任务不能丢失（等待剩余任务执行完再关闭或者能够把剩余的任务吐出来还给用户）
3. 线程池最终关闭前，确保创建的所有工作线程都已退出，不会出现资源的泄露
#####
线程池自启动后便会有大量的工作线程在内部持续不断并发的执行提交的各种任务，而要想做到优雅停止并不是一件容易的事情。
因此ThreadPoolExecutor中最复杂的部分并不在于上文中的正常工作流程，而在于分散在各个地方但又紧密协作的，控制优雅停止的逻辑。
### ThreadPoolExecutor的其它功能
除了正常的工作流程以及优雅停止的功能外，ThreadPoolExecutor还提供了一些比较好用的功能
1. 提供了很多protected的钩子函数方便用户继承后进行各种拓展
2. 在运行时统计了总共执行的任务数等关键指标，并提供了对应的api便于用户在运行时观察运行状态
3. 允许在线程池运行过程中动态修改关键的配置参数（比如corePoolSize等），并实时的生效。

## jdk线程池ThreadPoolExecutor源码解析(自己动手实现线程池v1版本)
如费曼所说：What I can not create I do not understand（我不能理解我创造不了的东西）。
通过模仿jdk的ThreadPoolExecutor实现，从零开始实现一个线程池，可以迫使自己去仔细的捋清楚jdk线程池中设计的各种细节，加深理解以达到更好的学习效果。
#####
前面提到ThreadPoolExecutor的核心逻辑主要分为两部分，一是正常运行时处理提交的任务的逻辑，二是实现优雅停止的逻辑。
因此我们实现的线程池MyThreadPoolExecutor（以My开头用于区分）也会分为两个版本，v1版本只实现前一部分即正常运行时执行任务的逻辑，将有关线程池优雅停止的逻辑全部去除。
相比直接啃jdk最终实现的源码，v1版本的实现会更简单更易理解，让正常执行任务时的逻辑更加清洗而不会耦合太多优雅停止的逻辑。

### 线程池关键成员变量介绍
ThreadPoolExecutor中有许多的成员变量，大致可以分为三类。
#### 可由用户自定义的、用于控制线程池运行的配置参数
1. volatile int corePoolSize（最大核心线程数量）
2. volatile int maximumPoolSize（最大线程数量）
3. volatile long keepAliveTime（idle线程保活时间）
4. final BlockingQueue workQueue（工作队列（阻塞队列））
5. volatile ThreadFactory threadFactory（工作线程工厂）
6. volatile RejectedExecutionHandler handler（拒绝异常处理器）
7. volatile boolean allowCoreThreadTimeOut（是否允许核心线程在idle超时后退出）
#####
其中前6个配置参数都可以在ThreadPoolExecutor的构造函数中指定，而allowCoreThreadTimeOut则可以通过暴露的public方法allowCoreThreadTimeOut来动态的设置。  
而且其中大部分属性都是volatile修饰的，目的是让运行过程中可以用过提供的public方法动态修改这些值后，线程池中的活跃的工作线程或者提交任务的用户线程能及时的感知到变化（线程间的可见性），并进行响应（比如令核心线程自动的idle退出）  
这些配置属性具体如何控制线程池行为的原理都会在下面的源码解析中展开介绍。理解这些参数的工作原理后才能在实际的业务中使用线程池时为其设置合理的值。
#### 仅供线程池内部工作时使用的属性
1. ReentrantLock mainLock（用于控制各种临界区逻辑的并发）
2. HashSet<Worker> workers（当前活跃工作线程Worker的集合，工作线程的工作原理会在下文介绍）
3. AtomicInteger ctl（线程池控制状态，control的简写）
#####
这里重点介绍一下ctl属性。ctl虽然是一个32位的整型字段（AtomicInteger），但实际上却用于标识两个业务属性，即当前线程池的运行状态和worker线程的总数量。  
在线程池初始化时状态位RUNNING，worker线程数量位0（private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));）。  
ctl的32位中的高3位用于标识线程池当前的状态，剩余的29位用于标识线程池中worker线程的数量（因此理论上ThreadPoolExecutor最大可容纳的线程数并不是2^31-1(32位中符号要占一位)，而是2^29-1）  
由于聚合之后单独的读写某一个属性不是很方便，所以ThreadPoolExecutor中提供了很多基于位运算的辅助函数来简化这些逻辑。  
#####
**ctl这样聚合的设计比起拆分成两个独立的字段有什么好处？**  
在ThreadPoolExecutor中关于优雅停止的逻辑中有很多地方是需要**同时判断**当前工作线程数量与线程池状态后，再对线程池状态工作线程数量进行更新的（具体逻辑在下一篇v2版本的博客中展开）。  
且为了执行效率，不使用互斥锁而是通过cas重试的方法来解决并发更新的问题。而对一个AtomicInteger属性做cas重试的更新，要比同时控制两个属性进行cas的更新要简单很多，执行效率也高很多。
#####
ThreadPoolExecutor共有五种状态，但有四种都和优雅停止有关（除了RUNNING）。
但由于v1版本的MyThreadPoolExecutorV1不支持优雅停止，所以不在本篇博客中讲解这些状态具体的含义以及其是如何变化的（下一篇v2版本的博客中展开）

#### 记录线程池运行过程中的一些关键指标
1. completedTaskCount（线程池自启动后已完成的总任务数）
2. largestPoolSize（线程池自启动后工作线程个数的最大值）
在运行过程中，ThreadPoolExecutor会在对应的地方进行埋点，统计一些指标并提供相应的api给用户实时的查询，以提高线程池工作时的可观测性。
#####
```java
public class MyThreadPoolExecutorV1 implements MyThreadPoolExecutor{
    
   /**
    * 指定的最大核心线程数量
    * */
   private volatile int corePoolSize;

   /**
    * 指定的最大线程数量
    * */
   private volatile int maximumPoolSize;

   /**
    * 线程保活时间(单位：纳秒 nanos)
    * */
   private volatile long keepAliveTime;

   /**
    * 存放任务的工作队列(阻塞队列)
    * */
   private final BlockingQueue<Runnable> workQueue;

   /**
    * 线程工厂
    * */
   private volatile ThreadFactory threadFactory;

   /**
    * 拒绝策略
    * */
   private volatile MyRejectedExecutionHandler handler;

   /**
    * 是否允许核心线程在idle一定时间后被销毁（和非核心线程一样）
    * */
   private volatile boolean allowCoreThreadTimeOut;

   /**
    * 主控锁
    * */
   private final ReentrantLock mainLock = new ReentrantLock();

   /**
    * 当前线程池已完成的任务数量
    * */
   private long completedTaskCount;

   /**
    * 维护当前存活的worker线程集合
    * */
   private final HashSet<MyWorker> workers = new HashSet<>();

   /**
    * 当前线程池中存在的worker线程数量 + 状态的一个聚合（通过一个原子int进行cas，来避免对两个业务属性字段加锁来保证一致性）
    * v1版本只关心前者，即worker线程数量
    */
   private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
   private static final int COUNT_BITS = Integer.SIZE - 3;

   /**
    * 32位的有符号整数，有3位是用来存放线程池状态的，所以用来维护当前工作线程个数的部分就只能用29位了
    * 被占去的3位中，有1位原来的符号位，2位是原来的数值位。
    * */
   private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

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
   private static final int RUNNING = -1 << COUNT_BITS;
   private static final int SHUTDOWN = 0 << COUNT_BITS;
   private static final int STOP = 1 << COUNT_BITS;
   private static final int TIDYING = 2 << COUNT_BITS;
   private static final int TERMINATED = 3 << COUNT_BITS;

   // Packing and unpacking ctl
   private static int workerCountOf(int c)  { return c & CAPACITY; }
   private static int ctlOf(int rs, int wc) { return rs | wc; }

   /**
    * 跟踪线程池曾经有过的最大线程数量（只能在mainLock的并发保护下更新）
    */
   private int largestPoolSize;

   private boolean compareAndIncrementWorkerCount(int expect) {
      return this.ctl.compareAndSet(expect, expect + 1);
   }
   private boolean compareAndDecrementWorkerCount(int expect) {
      return ctl.compareAndSet(expect, expect - 1);
   }

   private void decrementWorkerCount() {
      do {
         // cas更新，workerCount自减1
      } while (!compareAndDecrementWorkerCount(ctl.get()));
   }

   public MyThreadPoolExecutorV1(int corePoolSize,
                                 int maximumPoolSize,
                                 long keepAliveTime,
                                 TimeUnit unit,
                                 BlockingQueue<Runnable> workQueue,
                                 ThreadFactory threadFactory,
                                 MyRejectedExecutionHandler handler) {
      // 基本的参数校验
      if (corePoolSize < 0 || maximumPoolSize <= 0 || maximumPoolSize < corePoolSize || keepAliveTime < 0) {
         throw new IllegalArgumentException();
      }

      if (unit == null || workQueue == null || threadFactory == null || handler == null) {
         throw new NullPointerException();
      }

      // 设置成员变量
      this.corePoolSize = corePoolSize;
      this.maximumPoolSize = maximumPoolSize;
      this.workQueue = workQueue;
      this.keepAliveTime = unit.toNanos(keepAliveTime);
      this.threadFactory = threadFactory;
      this.handler = handler;
   }
}
```
##### Worker工作线程

##### 提交任务execute
1. addWorker
2. addWorkerFailed
3. runWorker
4. getTask
5. processWorkerExit
##### jdk默认的四种拒绝策略
##### jdk默认的四种线程池实现（todo）
##### 动态修改配置参数
1. allowCoreThreadTimeOut
2. setCorePoolSize
3. setMaximumPoolSize

### 总结