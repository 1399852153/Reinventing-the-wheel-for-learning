#jdk调度任务线程池ScheduledThreadPoolExecutor工作原理解析
在日常开发中存在着调度延时任务、定时任务的需求，而jdk中提供了两种基于内存的任务调度工具，即相对早期的java.util.Timer类和java.util.concurrent中的ScheduledThreadPoolExecutor。  
#####Timer介绍
Timer类其底层基于二叉堆实现的优先级队列，使得当前最早应该被执行的任务始终保持在队列头，并能以O(log n)对数复杂度完成任务的入队和出队。  
Timer是比较早被引入jdk的，其只支持单线程处理任务，因此如果先被处理的任务比较耗时便会阻塞后续任务的执行，进而导致任务调度不够及时（比如本来10分钟后要执行的任务，被拖延到30分钟后才执行）  
#####ScheduledThreadPoolExecutor介绍
Timer调度器的改进版本ScheduledThreadPoolExecutor在jdk1.5中随着juc包一起被引入。   
* ScheduledThreadPoolExecutor能够支持调度一次性的延迟任务，和固定频率/固定延迟的定时任务（这两种定时任务的具体区别会在下文展开）
* ScheduledThreadPoolExecutor其底层同样基于二叉堆实现的优先级队列来存储任务的，能做到对数时间复杂度的任务入队和出队
  与Timer不同的是，ScheduledThreadPoolExecutor作为juc下ThreadPoolExecutor的子类拓展，支持多线程并发的处理提交的调度任务。  
* 在线程池并发线程数合理且任务执行耗时也合理的情况下，一般不会出现之前被调度的任务阻塞后续任务调度的情况。  
  但反之，如果同一时间内需要调度的任务过多超过了线程池并发的负荷或者某些任务执行时间过长导致工作线程被长时间占用，则ScheduledThreadPoolExecutor无法保证实时的调度。
## ScheduledThreadPoolExecutor工作原理

### ScheduledThreadPoolExecutor的缺点

## 总结