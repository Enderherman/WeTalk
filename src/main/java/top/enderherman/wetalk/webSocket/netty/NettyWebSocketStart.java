package top.enderherman.wetalk.webSocket.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.config.AppConfig;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NettyWebSocketStart implements Runnable {

    private static final EventLoopGroup bossGroup = new NioEventLoopGroup(1);

    private static final EventLoopGroup workGroup = new NioEventLoopGroup();

    @Resource
    private HeartBeatHandler heartBeatHandler;

    @Resource
    private WebSocketHandler webSocketHandler;

    @Resource
    private AppConfig appConfig;


    @PreDestroy
    public void close() {
        bossGroup.shutdownGracefully();
        workGroup.shutdownGracefully();
    }

    @Override
    public void run() {
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workGroup);
            serverBootstrap.channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.DEBUG)).childHandler(new ChannelInitializer() {
                @Override
                protected void initChannel(Channel channel) throws Exception {
                    ChannelPipeline pipeline = channel.pipeline();
                    //支持Http协议 使用Http编码器
                    pipeline.addLast(new HttpServerCodec());
                    //聚合解码
                    //保证接受到Http的完整性
                    pipeline.addLast(new HttpObjectAggregator(64 * 1024));
                    //心跳 long readerIdleTime, long writerIdleTime, long allIdleTime, TimeUnit unit
                    //readerIdleTime读超时时间 客户端一定时间内未接收到服务端消息
                    //writerIdleTime写超时时间 客户端一定时间内未向服务端发送消息
                    //allIdleTime 所有类型超时时间
                    pipeline.addLast(new IdleStateHandler(6, 0, 0, TimeUnit.SECONDS));
                    //超时处理器
                    pipeline.addLast(heartBeatHandler);
                    //将Http协议升级为ws协议 支持webSocket
                    //String websocketPath, String subprotocols, boolean allowExtensions, int maxFrameSize, boolean allowMaskMismatch, boolean checkStartsWith
                    pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, true, 64 * 1024, true, true, 10000L));
                    pipeline.addLast(webSocketHandler);
                }
            });

            ChannelFuture channelFuture = serverBootstrap.bind(appConfig.getWsPost()).sync();
            log.info("netty启动成功, 端口:{}", appConfig.getWsPost());
            channelFuture.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            log.error("netty启动失败", e);
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }


}
