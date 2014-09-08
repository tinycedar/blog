import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.lang.Integer;
import java.lang.StringBuilder;

public class AioServer{
    public static void main(String[] args) throws Exception{
        new AioServer().init();
        System.out.println("Server has started successfully...");
        Thread.sleep(Integer.MAX_VALUE);
    }

    public void init() throws Exception{
        final AsynchronousServerSocketChannel listener = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(8081));
        listener.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>(){
            public void completed(AsynchronousSocketChannel channel, Void att){
                listener.accept(null, this);
                handle(channel);
            }

            public void failed(Throwable throwable, Void att){
                System.err.println(throwable);
            }
        });
    }

    private void handle(AsynchronousSocketChannel channel){
        try{
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 200 OK\r\n");
            sb.append("Content-Type: text/html; charset=UTF-8\r\n\r\n");
            sb.append("<doctype !html><html><head><title>Hello</title>");
            sb.append("<style>body { background-color: #111 }");
            sb.append("h1 { font-size:4cm; text-align: center; color: black;");
            sb.append("text-shadow: 0 0 2mm red}</style></head>");
            sb.append("<body><h1>Hello AIO !</h1></body></html>\r\n");
            channel.write(ByteBuffer.wrap(sb.toString().getBytes()));
            channel.shutdownOutput();
            channel.close();
        }catch(IOException ex){
            System.err.println(ex);
        }
    }
}
