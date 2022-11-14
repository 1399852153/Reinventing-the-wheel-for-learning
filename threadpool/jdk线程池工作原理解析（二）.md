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

    /**
     * 推进线程池工作状态
     * */
    private void advanceRunState(int targetState) {
        for(;;){
            // 获得当前的线程池状态
            int currentCtl = this.ctl.get();

            // 1 （runState >= targetState）如果当前线程池状态不比传入的targetState小
            // 代表当前状态已经比参数要制定的更加快(或者至少已经处于对应阶段了)，则无需更新poolStatus的状态(或语句中第一个条件为false，直接break了)
            // 2  (this.ctl.compareAndSet)，cas的将runState更新为targetState
            // 如果返回true则说明cas更新成功直接break结束（或语句中第一个条件为false，第二个条件为true）
            // 如果返回false说明cas争抢失败，再次进入while循环重试（或语句中第一个和第二个条件都是false，不break而是继续执行循环重试）
            if (runStateAtLeast(currentCtl, targetState) ||
                    this.ctl.compareAndSet(
                            currentCtl,
                            ctlOf(targetState, workerCountOf(currentCtl)
                            ))) {
                break;
            }
        }
    }
}    
```
因为线程池状态不是单独存放，而是放在ctl这一32位数据的高3位的，读写都比较麻烦，因此提供了runStateOf和ctlOf方法（位运算）来简化操作。  
线程池的状态是单调递推的，由于巧妙的设置了状态靠前的值会更小，因此可以直接比较状态的值来判断当前线程池状态是否推进到了指定的状态（runStateLessThan、runStateAtLeast、isRunning、advanceRunState）。
## jdk线程池ThreadPoolExecutor优雅停止具体实现原理
线程池的优雅停止一般要能做到以下几点：
1. 线程池在中止后不能再受理新的任务
2. 线程池中止的过程中，已经提交的现存任务不能丢失（等待剩余任务执行完再关闭或者能够把剩余的任务吐出来还给用户）
3. 线程池最终关闭前，确保创建的所有工作线程都已退出，不会出现资源的泄露
下面我们从源码层面解析ThreadPoolExecutor，看看其是如何实现上述这三点的.
### 如何中止线程池
ThreadPoolExecutor线程池提供了shutdown和shutdownNow这两个public方法给使用者用于发起线程池的停止指令。
##### shutdown方法
shutdown方法用于关闭线程池，并令线程池从RUNNING状态转变位SHUTDOWN状态。位于SHUTDOWN状态的线程池，不再接收新任务，但已提交的任务会全部被执行完。
```java
    /**
     * 关闭线程池（不再接收新任务，但已提交的任务会全部被执行）
     * 但不会等待任务彻底的执行完成（awaitTermination）
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;

        // shutdown操作中涉及大量的资源访问和更新，直接通过互斥锁防并发
        mainLock.lock();
        try {
            // 用于shutdown/shutdownNow时的安全访问权限
            checkShutdownAccess();
            // 将线程池状态从RUNNING推进到SHUTDOWN
            advanceRunState(SHUTDOWN);
            // shutdown不会立即停止所有线程，而仅仅先中断idle状态的多余线程进行回收，还在执行任务的线程就慢慢等其执行完
            interruptIdleWorkers();
            // 单独为ScheduledThreadPoolExecutor开的一个钩子函数（hook for ScheduledThreadPoolExecutor）
            onShutdown();
        } finally {
            mainLock.unlock();
        }

        // 尝试终止线程池
        tryTerminate();
    }

    /**
     * 用于shutdown/shutdownNow时的安全访问权限
     * 检查当前调用者是否有权限去通过interrupt方法去中断对应工作线程
     * */
    private void checkShutdownAccess() {
        // 判断jvm启动时是否设置了安全管理器SecurityManager
        SecurityManager security = System.getSecurityManager();
        // 如果没有设置，直接返回无事发生

        if (security != null) {
            // 设置了权限管理器，验证当前调用者是否有modifyThread的权限
            // 如果没有，checkPermission会抛出SecurityException异常
            security.checkPermission(shutdownPerm);
    
            // 通过上述校验，检查工作线程是否能够被调用者访问
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (MyWorker w : workers) {
                    // 检查每一个工作线程中的thread对象是否有权限被调用者访问
                    security.checkAccess(w.thread);
                }
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * 中断所有处于idle状态的线程
     * */
    private void interruptIdleWorkers() {
        // 默认打断所有idle状态的工作线程
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /**
     * 中断处于idle状态的线程
     * @param onlyOne 如果为ture，至多只中断一个工作线程(可能一个都不中断)
     *                如果为false，中断workers内注册的所有工作线程
     * */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (MyWorker w : workers) {
                Thread t = w.thread;
                // 1. t.isInterrupted()，说明当前线程存在中断信号，之前已经被中断了，无需再次中断
                // 2. w.tryLock(), runWorker方法中如果工作线程获取到任务开始工作，会先进行Lock加锁
                // 则这里的tryLock会加锁失败，返回false。 而返回true的话，就说明当前工作线程是一个idle线程，需要被中断
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        // tryLock成功时，会将内部state的值设置为1，通过unlock恢复到未加锁的状态
                        w.unlock();
                    }
                }
                if (onlyOne) {
                    // 参数onlyOne为true，至多只中断一个工作线程
                    // 即使上面的t.interrupt()没有执行，也在这里跳出循环
                    break;
                }
            }
        } finally {
            mainLock.unlock();
        }
    }
    
    /**
     * 单独为jdk的ScheduledThreadPoolExecutor开的一个钩子函数
     * 由ScheduledThreadPoolExecutor继承ThreadExecutor时重写（包级别访问权限）
     * */
    void onShutdown() {}
```
#####
1. shutdown方法在入口处使用mainLock加锁后，通过checkShutdownAccess检查当前是否有权限访问工作线程（前提是设置了SecurityManager），如果无权限则会抛出SecurityException异常。
2. 通过advanceRunState方法将线程池状态推进到SHUTDOWN。
3. 通过interruptIdleWorkers使用中断指令（Thread.interrupt）唤醒所有处于idle状态的工作线程（存在idle状态的工作线程代表着当前工作队列是空的）。 
   idle的工作线程在被唤醒后从getTask方法中退出（getTask中对应的退出逻辑在下文中展开），进而退出runWorker方法，最终系统回收掉工作线程占用的各种资源（第一篇博客中runWorker的解析中提到过）。
4. 调用包级别修饰的钩子函数onShutdown。这一方法是作者专门为同为java.util.concurrent包下的ScheduledThreadPoolExecutor提供的拓展。具体原理不再本篇博客中展开。
5. 前面提到SHUTDOWN状态的线程池在工作线程都全部退出且工作队列为空时会转变为TIDYING状态，因此通过调用tryTerminate方法**尝试**终止线程池（当前不一定会满足条件，比如调用了shutdown但工作队列还有很多任务等待执行）。
   tryTerminate方法中细节比较多，下文中再展开分析。
##### shutdownNow方法
shutdownNow方法同样用于关闭线程池，但比shutdown方法更加激进。shutdownNow方法令线程池从RUNNING状态转变为STOP状态，不再接收新任务，工作队列中未完成的任务会以列表的形式返回。  
* shutdown方法在调用后，虽然不再接受新任务，但会等待工作队列中的队列被慢慢消费掉；而shutdownNow并不会等待，而是会将当前工作队列中的所有未被捞取的剩余任务全部返回给shutdownNow的调用者，并将所有的工作线程(包括非idle的线程)都打断。  
* 这样做的好处是线程池可以更快的进入终止态，而不必等所有的剩余任务完成，且返回给用户后也不会丢任务。
```java
    /**
     * 立即关闭线程池（不再接收新任务，工作队列中未完成的任务会以列表的形式返回）
     * @return 当前工作队列中未完成的任务
     * */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;

        final ReentrantLock mainLock = this.mainLock;

        // shutdown操作中涉及大量的资源访问和更新，直接通过互斥锁防并发
        mainLock.lock();
        try {
            // 用于shutdown/shutdownNow时的安全访问权限
            checkShutdownAccess();
            // 将线程池状态从RUNNING推进到STOP
            advanceRunState(STOP);
            interruptWorkers();

            // 将工作队列中未完成的任务提取出来(会清空线程池的workQueue)
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }

        // 尝试终止线程池
        tryTerminate();
        return tasks;
    }

   /**
    * shutdownNow方法内，立即终止线程池时该方法被调用
    * 中断通知所有已经启动的工作线程（比如等待在工作队列上的idle工作线程，或者run方法内部await、sleep等，令其抛出中断异常快速结束）
    * */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (MyWorker w : workers) {
              // 遍历所有的worker线程，已启动的工作线程全部调用Thread.interrupt方法，发出中断信号
              w.interruptIfStarted();
            }
        } finally {
            mainLock.unlock();
        }
    }

   /**
    * 将工作队列中的任务全部转移出来
    * 用于shutdownNow紧急关闭线程池时将未完成的任务返回给调用者，避免任务丢失
    * */
   private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> queue = this.workQueue;
        ArrayList<Runnable> taskList = new ArrayList<>();
        queue.drainTo(taskList);
        // 通常情况下，普通的阻塞队列的drainTo方法可以一次性的把所有元素都转移到taskList中
        // 但jdk的DelayedQueue或者一些自定义的阻塞队列，drainTo方法无法转移所有的元素
        // （比如DelayedQueue的drainTo方法只能转移已经不需要延迟的元素，即getDelay()<=0）
        if (!queue.isEmpty()) {
           // 所以在这里打一个补丁逻辑：如果drainTo方法执行后工作队列依然不为空，则通过更基础的remove方法把队列中剩余元素一个一个的循环放到taskList中
           for (Runnable r : queue.toArray(new Runnable[0])) {
              if (queue.remove(r)) {
                taskList.add(r);
              }
           }
        }
        
        return taskList;
   }    
```
#####
1. shutdownNow方法在入口处使用mainLock加锁后，与shutdown方法一样也通过checkShutdownAccess检查当前是否有权限访问工作线程（前提是设置了SecurityManager），如果无权限则会抛出SecurityException异常。
2. 通过advanceRunState方法将线程池状态推进到STOP。
3. 通过interruptWorkers使用中断指令（Thread.interrupt）唤醒所有工作线程（区别于shutdown中的interruptIdleWorkers）。区别在于除了idle的工作线程，所有正在执行任务的工作线程也会收到中断通知，**期望**其能尽快退出任务的执行。
4. 通过drainQueue方法将当前工作线程中剩余的所有任务以List的形式统一返回给调用者。
5. 通过调用tryTerminate方法**尝试**终止线程池。
### 如何保证线程池在中止后不能再受理新的任务？
在execute方法作为入口，提交任务的逻辑中，v2版本（和jdk实现基本一致）相比v1版本新增了一些基于线程池状态的校验。
##### execute方法中的校验
* 首先在最外层的execute方法中，在向工作队列中加入新任务前（workQueue.offer）对当前线程池的状态做了一个校验（isRunning(currentCtl)）。希望非RUNNING状态的线程池不向工作队列中添加新任务  
  但在检查时可能出与shutdown指令并发，所有在向工作队列成功加入任务后还需要再检查一次线程池状态，如果此时已经不是RUNNING状态则需要通过remove方法将刚入队的任务从队列中移除，并调用reject方法走拒绝策略
##### addWorker方法中的校验
* 在addWorker方法的入口处(retry:第一层循环通过(runState >= SHUTDOWN && !(runState == SHUTDOWN && firstTask == null && !workQueue.isEmpty())))逻辑，
  保证了不是RUNNING状态的线程池（runState >= SHUTDOWN），无法创建新的工作线程（addWorker返回false）。  
  **但有一种特殊情况**：即SHUTDOWN状态下(runState == SHUTDOWN)，工作队列不为空(!workQueue.isEmpty())，且不是第一次提交任务时创建新工作线程（firstTask == null），  
  依然允许创建新的工作线程，因为即使在SHUTDOWN状态下，某一存活的工作线程发生中断异常时，会调用processWorkerExit方法，在销毁原有工作线程后依然需要调用addWorker重新创建一个新的（firstTask == null）
##### execute与shutdown/shutdownNow并发时的处理
execute提交任务时addWorker方法和shutdown/shutdownNow方法是可能并发执行的，但addWorker中有多处地方都对线程池的状态进行了检查，尽最大的可能避免线程池停止时并发的创建新的工作线程。
1. retry循环中，compareAndIncrementWorkerCount方法会cas的更新状态（此前获取到的ctl状态必然是RUNNING，否则走不到这里），cas成功则会跳出retry:循环（ break retry;）。 
   而cas失败可能有两种情况： 
   如果是workerCount发生了并发的变化，则在内层的for (;;)循环中进行重试即可  
   如果线程池由于收到终止指令而推进了状态，则随后的if (runStateOf(currentCtl) != runState)将会为true，跳出到外层的循环重试（continue retry）  
2. 在new Worker(firstTask)后，使用mainLock获取锁后再一次检查线程池状态（if (runState < SHUTDOWN ||(runState == SHUTDOWN && firstTask == null))）。  
   由于shutdown、shutdownNow也是通过mainLock加锁后才推进的线程池状态，因此这里的获取到的状态是准确的。
   如果校验失败（if结果为false），则workers中不会加入新创建的工作线程，临时变量workerAdded=false，则工作线程不会启动（t.start()）。临时变量workerStarted也为false，最后会调用addWorkerFailed将新创建的工作线程给回收（回滚）
#####
基于execute方法和addWorker方法中关于各项关于线程池停止状态校验，**最大程度的避免了线程池在停止过程中新任务的提交和可能的新工作线程的创建**。使得execute方法在线程池接收到停止指令后（>=SHUTDOWN），都统一的执行reject拒绝策略逻辑。
```java
/**
     * 提交任务，并执行
     * */
    @Override
    public void execute(Runnable command) {
        if (command == null){
            throw new NullPointerException("command参数不能为空");
        }

        int currentCtl = this.ctl.get();
        if (workerCountOf(currentCtl) < this.corePoolSize) {
            // 如果当前存在的worker线程数量低于指定的核心线程数量，则创建新的核心线程
            boolean addCoreWorkerSuccess = addWorker(command,true);
            if(addCoreWorkerSuccess){
                // addWorker添加成功，直接返回即可
                return;
            }

            // addWorker失败了
            // 失败的原因主要有以下几个：
            // 1 线程池的状态出现了变化，比如调用了shutdown/shutdownNow方法，不再是RUNNING状态，停止接受新的任务
            // 2 多个线程并发的execute提交任务，导致cas失败，重试后发现当前线程的个数已经超过了限制
            // 3 小概率是ThreadFactory线程工厂没有正确的返回一个Thread

            // 获取最新的ctl状态
            currentCtl = this.ctl.get();
        }

        // 走到这里有两种情况
        // 1 因为核心线程超过限制（workerCountOf(currentCtl) < corePoolSize == false），需要尝试尝试将任务放入阻塞队列
        // 2 addWorker返回false，创建核心工作线程失败

        // 判断当前线程池状态是否为running
        // 如果是running状态，则进一步执行任务入队操作
        if(isRunning(currentCtl) && this.workQueue.offer(command)){
            // 线程池是running状态，且workQueue.offer入队成功

            int recheck = this.ctl.get();
            // 重新检查状态，避免在上面入队的过程中线程池并发的关闭了
            // 如果是isRunning=false，则进一步需要通过remove操作将刚才入队的任务删除，进行回滚
            if (!isRunning(recheck) && remove(command)) {
                // 线程池关闭了，执行reject操作
                reject(command);
            } else if(workerCountOf(currentCtl) == 0){
                // 在corePoolSize为0的情况下，当前不存在存活的核心线程
                // 一个任务在入队之后，如果当前线程池中一个线程都没有，则需要兜底的创建一个非核心线程来处理入队的任务
                // 因此firstTask为null，目的是先让任务先入队后创建线程去拉取任务并执行
                addWorker(null,false);
            }else{
                // 加入队列成功，且当前存在worker线程，成功返回
                return;
            }
        }else{
            // 阻塞队列已满，尝试创建一个新的非核心线程处理
            boolean addNonCoreWorkerSuccess = addWorker(command,false);
            if(!addNonCoreWorkerSuccess){
                // 创建非核心线程失败，执行拒绝策略（失败的原因和前面创建核心线程addWorker的原因类似）
                reject(command);
            }else{
                // 创建非核心线程成功，成功返回
                return;
            }
        }
    }
```
```java
/**
     * 向线程池中加入worker
     * */
    private boolean addWorker(Runnable firstTask, boolean core) {
        // retry标识外层循环
        retry:
        for (;;) {
            int currentCtl = ctl.get();
            int runState = runStateOf(currentCtl);

            // Check if queue empty only if necessary.
            // 线程池终止时需要返回false,避免新的worker被创建
            // 1 先判断runState >= SHUTDOWN
            // 2 runState >= SHUTDOWN时，意味着不再允许创建新的工作线程，但有一种情况例外
            // 即SHUTDOWN状态下(runState == SHUTDOWN)，工作队列不为空(!workQueue.isEmpty())，还需要继续执行
            // 比如在当前存活的线程发生中断异常时，会调用processWorkerExit方法，在销毁原有工作线程后调用addWorker重新创建一个新的（firstTask == null）
            if (runState >= SHUTDOWN && !(runState == SHUTDOWN && firstTask == null && !workQueue.isEmpty())) {
                // 线程池已经是关闭状态了，不再允许创建新的工作线程，返回false
                return false;
            }

            // 用于cas更新workerCount的内层循环（注意这里面与jdk的写法不同，改写成了逻辑一致但更可读的形式）
            for (;;) {
                // 判断当前worker数量是否超过了限制
                int workerCount = workerCountOf(currentCtl);
                if (workerCount >= CAPACITY) {
                    // 当前worker数量超过了设计上允许的最大限制
                    return false;
                }
                if (core) {
                    // 创建的是核心线程，判断当前线程数是否已经超过了指定的核心线程数
                    if (workerCount >= this.corePoolSize) {
                        // 超过了核心线程数，创建核心worker线程失败
                        return false;
                    }
                } else {
                    // 创建的是非核心线程，判断当前线程数是否已经超过了指定的最大线程数
                    if (workerCount >= this.maximumPoolSize) {
                        // 超过了最大线程数，创建非核心worker线程失败
                        return false;
                    }
                }

                // cas更新workerCount的值
                boolean casSuccess = compareAndIncrementWorkerCount(currentCtl);
                if (casSuccess) {
                    // cas成功，跳出外层循环
                    break retry;
                }

                // 重新检查一下当前线程池的状态与之前是否一致
                currentCtl = ctl.get();  // Re-read ctl
                if (runStateOf(currentCtl) != runState) {
                    // 从外层循环开始continue（因为说明在这期间 线程池的工作状态出现了变化，需要重新判断）
                    continue retry;
                }

                // compareAndIncrementWorkerCount方法cas争抢失败，重新执行内层循环
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;

        MyWorker newWorker = null;
        try {
            // 创建一个新的worker
            newWorker = new MyWorker(firstTask);
            final Thread myWorkerThread = newWorker.thread;
            if (myWorkerThread != null) {
                // MyWorker初始化时内部线程创建成功

                // 加锁，防止并发更新
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();

                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int runState = runStateOf(ctl.get());

                    // 重新检查线程池运行状态，满足以下两个条件的任意一个才创建新Worker
                    // 1 runState < SHUTDOWN
                    // 说明线程池处于RUNNING状态正常运行，可以创建新的工作线程
                    // 2 runState == SHUTDOWN && firstTask == null
                    // 说明线程池调用了shutdown，但工作队列不为空，依然需要新的Worker。
                    // firstTask == null标识着其不是因为外部提交新任务而创建新Worker，而是在消费SHUTDOWN前已提交的任务
                    if (runState < SHUTDOWN ||
                            (runState == SHUTDOWN && firstTask == null)) {
                        if (myWorkerThread.isAlive()) {
                            // 预检查线程的状态，刚初始化的worker线程必须是未唤醒的状态
                            throw new IllegalThreadStateException();
                        }

                        // 加入worker集合
                        this.workers.add(newWorker);

                        int workerSize = workers.size();
                        if (workerSize > largestPoolSize) {
                            // 如果当前worker个数超过了之前记录的最大存活线程数，将其更新
                            largestPoolSize = workerSize;
                        }

                        // 创建成功
                        workerAdded = true;
                    }
                } finally {
                    // 无论是否发生异常，都先将主控锁解锁
                    mainLock.unlock();
                }

                if (workerAdded) {
                    // 加入成功，启动worker线程
                    myWorkerThread.start();
                    // 标识为worker线程启动成功，并作为返回值返回
                    workerStarted = true;
                }
            }
        }finally {
            if (!workerStarted) {
                addWorkerFailed(newWorker);
            }
        }

        return workerStarted;
    }
```
```java
    /***/
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        // 当一个任务从工作队列中被成功移除，可能此时工作队列为空。尝试判断是否满足线程池中止条件
        tryTerminate();
        return removed;
    }
```
### 如何保证中止过程中不丢失已提交的任务？
1. 等待剩余任务执行完再关闭
2. 把剩余的任务吐出来还给用户

### 如何保证线程池最终关闭前，所有工作线程都已退出？
##### 如何保证工作线程一定会退出？

## 总结

