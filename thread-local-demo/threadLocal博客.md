# ThreadLocal介绍(为什么Netty的FastThreadLocal更快?)
## 1 ThreadLocal简单介绍

## 2 自己实现一个极简ThreadLocal(很简单但是问题很大)

## 3 jdk的ThreadLocal工作原理分析
### 3.1 每一个thread带有一个map，每个threadLocal对象放在线程自己的localMap中(避免多线程竞争)
### 3.2 threadLocalMap采用线性探测法避免hash冲突，而不是使用拉链法。为什么与HashTable/HashMap不同？为什么HashMap不采用线性探测法
### 3.3 ThreadLocal的Entry是WeakReference，为什么要这么设计？避免内存泄露(A)
##### 强引用、弱引用、软应用、虚引用简单介绍

## 4 netty的FastThreadLocal工作原理
### 4.1 为什么FastThreadLocal更快？
### 4.2 FastThreadLocal存在哪些缺陷？(jdk的实现功能更加通用)

## 5 三种ThreadLocal语义实现的测试

## 总结