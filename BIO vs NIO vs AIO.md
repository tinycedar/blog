最近在看Tomcat 7.0的源码，一直对Tomcat以下几块比较感兴趣：
- 容器的架构和各组件的启动过程
- 容器Deploy一个Servlet webapp的过程
- 容器接受并处理客户端请求

而最后一块是我最感兴趣的部分，Tomcat 7.0提供了BIO(blocking I/O)和NIO(non-blocking I/O)及AJP协议的实现，
不过默认启用BIO和AJP，大家可以通过修改Tomcat安装目录下的conf/server.xml来启用NIO,
只需把protocol="HTTP/1.1"改为protocol="org.apache.coyote.http11.Http11NioProtocol"即可。

###Blocking I/O
JDK 4.0以前只有BIO的API，通常我们写Client/Server程序都会这么写：

```Java
ServerSocket ss = createServerSocket();
ss.bind(new InetSocketAddress(8080));
for (;;) {
	Socket socket = ss.accept();
	handle(socket);
}
```
一般在handle中会通过read()方法读取客户端请求数据，但如果I/O还未read ready，那么read()方法就会阻塞，那么上述代码会造成所有其它客户端请求全部无法处理，这会严重影响响应速度和吞吐量。

那么怎么解决这个问题呢？用multi-threading：
```Java
ServerSocket ss = createServerSocket();
ss.bind(new InetSocketAddress(8080));
for (;;) {
	Socket socket = ss.accept();
	new Handler(socket).start();
}
```
Handler为一个thread对象，此时每一个请求对应一个thread，即使有很多线程处于阻塞状态，也不会影响其它线程。但是由于线程的创建和销毁需要占用很多资源，所以上述代码可以用thread-pool来改进，这样就能有效利用、回收线程。
线程池的大小很关键：如果较小，客户端请求数大于线程池的部分只能排队等待；如果较大，context switch会占用很多时间，一次大概30,000 ns，也就意味着如果有1万个线程，至少需要300 ms后才能处理一遍所有线程，这样有点得不偿失。

###Non-blocking I/O
JDK 4.0正式引入NIO API；JDK中NIO其实指的是New I/O，既包括普通文件I/O也有针对网络I/O，目前我们只讨论网络I/O。Selector/Channel确实达到了non-blocking的目标，虽然本质上其还是同步而非异步，但是还是通过I/O多路复用基本实现了非阻塞的目标。

不同平台底层对Selector/Channel的实现不同，Windows基于IOCP，Linux Kernel基于select/poll或epoll，FreeBSD基于kqueue等等。不同平台的JDK的Selector/Channel有着不同的实现，但是提供给我们的接口一致。

```Java
Selector selector = Selector.open();
SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
while(true) {
	selector.select();
	Iterator iterator = selector.selectedKeys().iterator();
	while(iterator.hasNext()) {
    		SelectionKey key = iterator.next();
		new Handler(key).start();
	}
}
```
上述代码只用一个线程就能实现处理所有客户端的请求。注意Selector的select()方法为同步方法，如果当前没有I/O ready就会一直阻塞在那里，一旦只要有一个ready，就会返回并执行下面的代码。相比于BIO，Handler线程永远只处理I/O ready的客户端请求，这样大幅度提高了吞吐量。近年来比较火的Mina/Netty和Node.js底层也是类似的实现。

###Asynchronous I/O
JDK 7.0开始引入AIO API，不再需要轮询，这是真正的异步实现：
```Java
AsynchronousServerSocketChannel listener = AsynchronousServerSocketChannel
				.open();
		listener.bind(new InetSocketAddress(8080));
		listener.accept(null,
				new CompletionHandler<AsynchronousSocketChannel, Object>() {
					@Override
					public void completed(AsynchronousSocketChannel result,
							Object attachment) {
						// handle result
					}

					@Override
					public void failed(Throwable exc, Object attachment) {
						// handle fail
					}
				});
```

这几天打算在Tomcat里加上AIO Connector的实现。
