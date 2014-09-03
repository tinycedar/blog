What is "Reordering" and why it matters
===================
工作这几年一直在做Web相关的开发，一直忽略了最重要的*基础*。近几年各种新技术层出不穷，这些技术外表看起来
很新但大都基于几十年来一直都没怎么变的*基础*：比如为高并发而生的Google出的语言Go，Goroutine看起来很高大上
但还是是基于操作系统的线程，只不过比线程的调度粒度更小而已；比如宣传口号为构建实时系统、高并发而生Node.js，运行
于Chrome V8，底层I/O基于select/poll/epoll；比如今年超级火爆的虚拟化系统Docker底层基于Linux的LXC。
所以当你掌握*基础*的时候，这些“新技术”学起来也就会得心应手了。

最近在看*Java Concurrency in Practice*，经常碰到一个词——Reordering，由于我基础差导致难以正确理解Reordering的含义。
而这个词贯穿全书，线程不安全很大一部分是由于Reordering引起的，重要性显而易见。经过查阅相关资料，本文以下部分是本人
的对Reordering的一些浅显的理解。
***

前几天我在微信上贴了一段代码，让大家猜程序的所有可能运行结果，代码如下所示：
```Java
public class ReorderingTest {
    private static boolean ready;
    private static int number;

    private static class T extends Thread {
        public void run() {
            while (!ready)
                Thread.yield();
                System.out.println(number);
            }
        }
    }

    public static void main(String[] args) {
        new T().start();
        number = 42;
        ready = true;
    }
}
```
有童鞋回答结果打印出*42*，这当然没错，但只是其中一种可能。那么还有哪些可能的结果呢？
实际上它有可能输出*0*，或者甚至程序*永远不退出*。对于后者我还能接受，但是前者（输出*0*），
我就无法接受了：因为从代码上看，如果程序有输出那么ready肯定被设为true了，而```number = 42```
这行代码写在```ready = true```之前，所以看起来无论如何结果都不会是*0*。那么结果为*0*的唯一可能
就是```ready = true```先执行，然后线程T启动并读取了*ready*和*number*变量，最后
主线程接着执行```number = 42```。How could it be possible ?!

这种解释直接颠覆了一直不看书的我的三观：虽然多个线程之间代码可以交叉执行（interleaving），但是在同一个
线程内代码居然可以乱序执行...看来是我too young, too simple, too naive了。

那么下面我们来看看Reordering到底是什么。

##Reordering / OoOE
下面是Wiki对于Reordering或Out-of-order execution的定义：
>In computer engineering, out-of-order execution (OoOE or OOE) is a paradigm used in most high-performance microprocessors
>to make use of instruction cycles that would otherwise be wasted by a certain type of costly delay.
>In this paradigm, a processor executes instructions in an order governed by the availability of input data,
>rather than by their original order in a program.[1] In doing so, the processor can avoid being idle while
>data is retrieved for the next instruction in a program, processing instead the next instructions which are able to run immediately.

上面这段话说的很清楚了。
