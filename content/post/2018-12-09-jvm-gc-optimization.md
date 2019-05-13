---
slug: jvm-gc-optimization
title: '浅谈JVM GC调优'
date: 2018-12-09T15:09:26+08:00
draft: false
categories:
- 10000 Hours
---

JVM上的GC，解放了程序员的生产力，使内存手动管理变成了上古绝技。但Java应用动辄几十毫秒甚至秒级的暂停时间，成了所有Java开发者的梦魇，也成了C/C++/Rust开发者嘲笑Java的黑点。无数次我梦想Azul家的C4算法能开源，但那可是人家的核心技术和摇钱树。终于ZGC出现了，几乎和C4一模一样的算法实现......在等待ZGC变成stable的这段时间里，我们没办法还得老实调优GC。

## 先看效果
XX系统，做为XXXX部门**最底层**、**流量最大**的应用，接口RT(响应时间)的重要性显而易见；而影响RT稳定性的众多因素里，GC STW（Stop The World）引起的暂停时间或许是最容易被忽视的。下图是优化前后Young GC暂停时间对比，从**60ms左右** 降到了**10ms左右**。

![](https://ae01.alicdn.com/kf/UTB8t36yM4HEXKJk43Je761eeXXaC.png)

![](https://ae01.alicdn.com/kf/UTB8pIYnMVfFXKJk43Ot760IPFXar.png)

## 再看参数
### 0. 机器配置
- Docker容器：4核4G

### 1. 内存相关
```bash
-Xms1856m
-Xmx1856m
-Xmn1g
-XX:MetaspaceSize=128m
-XX:MaxMetaspaceSize=256m
```

### 2. GC相关
```bash
-XX:+UseConcMarkSweepGC
-XX:+UseParNewGC
-XX:MaxTenuringThreshold=2
-XX:CMSInitiatingOccupancyFraction=75
-XX:+UseCMSInitiatingOccupancyOnly
-XX:+ExplicitGCInvokesConcurrent
-XX:+ParallelRefProcEnabled
-XX:+CMSParallelInitialMarkEnabled
-XX:+UnlockDiagnosticVMOptions
-XX:ParGCCardsPerStrideChunk=1024
-XX:ParallelGCThreads=4
-XX:ConcGCThreads=3
-XX:-CMSClassUnloadingEnabled
-XX:-UseBiasedLocking
```

### 3. GC普通日志
```bash
-verbose:gc
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-XX:+PrintGCApplicationStoppedTime
-XX:+PrintPromotionFailure
-Xloggc:/{gc_log_path}/gc.log
```

### 4. GC安全点日志
```bash
-XX:+UseCountedLoopSafepoints
-XX:+PrintSafepointStatistics
-XX:PrintSafepointStatisticsCount=1
-XX:+UnlockDiagnosticVMOptions
-XX:-DisplayVMOutput
-XX:+LogVMOutput
-XX:LogFile=/dev/shm/vm-{your_app}.log
```

## 一般优化套路
GC优化不是一蹴而就的事情，网上有太多相关资料和推荐的GC参数，但是每个应用的类型、使用场景都不一样，所以很显然同一份GC参数不可能对所有应用都适用。GC调优是一件很费时间的事，你需要不断尝试不断迭代最终达到你满意的效果。这里列一下一般GC优化的基本姿势：

1. 确定目标：延迟、吞吐量等
2. 优化参数：各种参数迭代尝试
3. 验收结果：你满不满意？

### 关于前置知识
Hotspot的基础知识，网上可谓汗牛充栋，GC优化相关的文章基本都以JVM各内存区域介绍做为开头，接着是GC算法介绍，最后贴了一堆GC相关的参数。因此这里就不做重复介绍了，想了解的童鞋自行Google。

### 选择哪种GC算法？
其实现阶段Hotspot的GC并没有太多选择，JDK 8平台要么选择CMS要么G1。[R大](https://www.zhihu.com/question/24923005)的建议是：以8G为界，8G以下的选CMS；反之选G1。

## 几个重要的参数

对服务端程序来说，Young GC引起的暂停是最需要关注的，毕竟绝大部分时间Major/Full GC并不会被触发。因此GC调优基本就变成了如何尽可能缩短Young GC的暂停时间。下面说说可能对Young GC影响较大的参数。

### -XX:MaxTenuringThreshold=2
这或许是对Young GC影响最大的参数了。对象在Survivor区最多熬过多少次Young GC后晋升到年老代，JDK8里CMS 默认是6，其他如G1是15。对大部分应用来说，这个值建议设得小一点，让Survivor区的内存尽早晋升到年老代。

### -XX:ParallelGCThreads=4 & -XX:ConcGCThreads=3
并发收集线程数。看具体应用和CPU核心数，建议多次迭代，选择表现最好的数据。

### -XX:-UseBiasedLocking
偏向锁，会尝试把锁赋给第一个访问它的线程，取消同步块上的synchronized原语。在安全点日志里，可以看到很多RevokeBiased的纪录，高并发场景下建议取消。
### -XX:+UseCountedLoopSafepoints
Keeps safepoints in counted loops. Its default value is false. 强烈建议开启，具体原因详见以下部分分析。

## 关于SafePoint（安全点）优化
为什么会关注这个问题呢？在我们调优GC过程中，GC日志里总能发现比较大的GCApplicationStoppedTime，但是又不清楚到底是什么原因触发的。经过一系列Google，发现Hotspot提供了安全点相关日志选项，能输出所有暂停的原因和准确时间。具体参数详见```GC安全点日志```部分。

不了解安全点的童鞋，先看这篇**江南白衣**写的文章：[JVM的Stop The World，安全点，黑暗的地底世界](http://calvin1978.blogcn.com/articles/safepoint.html)，写得非常好，深入浅出。看完了是不是有点意犹未尽的感觉😄，应该还想问：“我大概了解了，但能不能给点实际的例子？”，OK，下面就来一个***简单必现***的SafePoint相关的demo：
```Java
public class TestBlockingThread {

    static Consumer<Long> sleep = millis -> {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    };

    static Thread t1 = new Thread(() -> {
        while (true) {
            long start = System.currentTimeMillis();
            sleep.accept(1000L);
            long cost = System.currentTimeMillis() - start;
            (cost > 1010L ? System.err : System.out).printf("thread: %s, costs %d ms\n", Thread.currentThread().getName(), cost);
        }
    });

    static Thread t2 = new Thread(() -> {
        while (true) {
            for (int i = 1; i <= 1000000000; i++) {
                boolean b = 1.0 / i == 0;
            }
            sleep.accept(10L);
        }
    });

    public static final void main(String[] args) {
        t1.start();
        sleep.accept(1500L);
        t2.start();
    }
}
```

输出结果：
```bash
thread: Thread-0, costs 1004 ms
thread: Thread-0, costs 5555 ms
thread: Thread-0, costs 1000 ms
thread: Thread-0, costs 1003 ms
thread: Thread-0, costs 1001 ms
thread: Thread-0, costs 1004 ms
thread: Thread-0, costs 1004 ms
thread: Thread-0, costs 1003 ms
thread: Thread-0, costs 1002 ms
thread: Thread-0, costs 1004 ms
thread: Thread-0, costs 2234 ms
thread: Thread-0, costs 1003 ms
```

我们启动了t1、t2两个线程，但是只有t1会向控制台打印。从代码角度看，在一个多核机器上，理论上t1是不受t2影响的，而且t1暂停时间不会超出1000ms太多。但是运行结果并不是这样，t1线程有长达**5555ms**的暂停😓，这显然并不是代码本身引起的，根据上面文章我们应该可以猜到大概原因：**counted loop结束处未插入SafePoint代码**，因为JVM认为counted loop的执行时间是可控的，但是现实世界中总避免不了一些大循环的代码。那么怎么强制在每一个counted loop后面插入SafePoint代码呢？JVM提供了这个参数：-XX:+UseCountedLoopSafepoints，重新跑上面demo能发现输出正常了。

在我们线上应用加了这个参数后，很多莫名其妙的GC暂停毛刺消失了，**强烈建议**所有应用都加上这个参数。

## 参考资料
- 《Java性能优化权威指南》
- 《深入理解Java虚拟机》
- [JVM的Stop The World，安全点，黑暗的地底世界](http://calvin1978.blogcn.com/articles/safepoint.html)