import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NioServer {

	private static final int THREAD_POOL_COUNT = 1;

	private static final int PORT = 8080;

	private Selector selector;

	private ExecutorService executor;

	public static void main(String[] args) throws IOException {
		NioServer server = new NioServer();
		server.init();
		server.start();
	}

	public void init() throws IOException {
		selector = Selector.open();
		ServerSocketChannel listener = ServerSocketChannel.open();
		listener.configureBlocking(false);
		listener.register(selector, SelectionKey.OP_ACCEPT);
		listener.bind(new InetSocketAddress(PORT));

		executor = Executors.newFixedThreadPool(THREAD_POOL_COUNT);
	}

	public void start() throws IOException {
		while (true) {
			if (selector.select() <= 0) {
				continue;
			}
			Iterator<SelectionKey> keyIterator = selector.selectedKeys()
					.iterator();
			while (keyIterator.hasNext()) {
				SelectionKey key = keyIterator.next();
				keyIterator.remove();
				executor.submit(new Handler(key));
			}
		}
	}

	class Handler implements Runnable {
		private SelectionKey key;

		public Handler(SelectionKey key) {
			this.key = key;
		}

		@Override
		public void run() {
			if (key.isAcceptable()) {
				accept(key.channel());
			} else if (key.isReadable()) {
				read(key.channel());
			} else if (key.isWritable()) {
				write((SocketChannel) key.channel());
			}
		}

		private void accept(SelectableChannel channel) {
			ServerSocketChannel serverSocketChannel = (ServerSocketChannel) channel;
			SocketChannel socketChannel;
			try {
				socketChannel = serverSocketChannel.accept();
				register(socketChannel, SelectionKey.OP_READ);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void read(SelectableChannel channel) {
			register(channel, SelectionKey.OP_WRITE);
		}

		private void write(SocketChannel channel) {
			StringBuilder sb = new StringBuilder();
			sb.append("HTTP/1.1 200 OK\r\n");
			sb.append("Content-Type: text/html; charset=UTF-8\r\n\r\n");
			sb.append("<doctype !html><html><head><title>Hello</title>");
			sb.append("<style>body { background-color: #111 }");
			sb.append("h1 { font-size:4cm; text-align: center; color: black;");
			sb.append("text-shadow: 0 0 2mm red}</style></head>");
			sb.append("<body><h1>Hello NIO !</h1></body></html>\r\n");
			try {
				channel.write(ByteBuffer.wrap(sb.toString().getBytes()));
				channel.shutdownOutput();
				channel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void register(SelectableChannel channel, int operation) {
			if (operation != SelectionKey.OP_ACCEPT
					&& operation != SelectionKey.OP_READ
					&& operation != SelectionKey.OP_WRITE) {
				throw new IllegalArgumentException(
						"Illegal Selection operation");
			}
			try {
				channel.configureBlocking(false);
				channel.register(selector, operation);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
