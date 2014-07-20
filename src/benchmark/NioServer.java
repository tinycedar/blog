import java.util.Iterator;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.lang.StringBuilder;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class NioServer{

  private Selector selector ;

  public static void main(String[] args) throws IOException{
    new NioServer().init();
    System.out.println("Server has started successfully...");
  }

  public void init() throws IOException{
    selector = Selector.open();

    ServerSocketChannel listener = ServerSocketChannel.open();
    listener.bind(new InetSocketAddress(8080));
    listener.configureBlocking(false);
    listener.register(selector, SelectionKey.OP_ACCEPT);

    while(true){
      if(selector.select() <= 0){
        continue;
      }
      Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
      while(keyIterator.hasNext()){
        SelectionKey key = keyIterator.next();
        keyIterator.remove();
        handle(key);
      }
    }
  }

  private void handle(SelectionKey key) throws IOException{
    if(key.isAcceptable()){
      ServerSocketChannel serverSocket = (ServerSocketChannel)key.channel();
      SocketChannel channel = serverSocket.accept();
      channel.configureBlocking(false);
      channel.register(selector, SelectionKey.OP_READ);
    }else if(key.isReadable()){
      SocketChannel channel = (SocketChannel)key.channel();
      channel.configureBlocking(false);
      channel.register(selector, SelectionKey.OP_WRITE);
    }else if(key.isWritable()){
      SocketChannel channel = (SocketChannel)key.channel();
      StringBuilder sb = new StringBuilder();
      sb.append("HTTP/1.1 200 OK\r\n");
      sb.append("Content-Type: text/html; charset=UTF-8\r\n\r\n");
      sb.append("<doctype !html><html><head><title>Hello</title>");
      sb.append("<style>body { background-color: #111 }");
      sb.append("h1 { font-size:4cm; text-align: center; color: black;");
      sb.append("text-shadow: 0 0 2mm red}</style></head>");
      sb.append("<body><h1>Hello NIO !</h1></body></html>\r\n");
      channel.write(ByteBuffer.wrap(sb.toString().getBytes()));
      channel.shutdownOutput();
      channel.close();
    }
  }
}
