package top.enderherman.wetalk.webSocket.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;

import jakarta.annotation.Resource;


@Slf4j
@ChannelHandler.Sharable
@Component
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {


    @Resource
    private RedisComponent redisComponent;

    @Resource
    private ChannelContextUtils channelContextUtils;

    /**
     * 通道就绪后 调用 一般用来做初始化
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("新的连接加入.....");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("连接断开.....");
        channelContextUtils.removeContext(ctx.channel());
    }


    /**
     * 用于接受心跳
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) throws Exception {
        Channel channel = ctx.channel();
        Attribute<String> attribute = channel.attr(AttributeKey.valueOf(channel.id().toString()));
        String userId = attribute.get();
        //log.info("收到用户: {} 的消息:{}", userId, textWebSocketFrame.text());
        redisComponent.saveUserHeartBeat(userId);
    }


    /**
     * 握手认证·
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete complete = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            String url = complete.requestUri();
            log.info("url:{}", url);
            String token = getToken(url);
            if (token == null) {
                ctx.channel().close();
            }
            log.info("token:{}", token);
            TokenUserInfoDto tokenUserInfoDto = redisComponent.getTokenUserInfoDto(token);
            if (tokenUserInfoDto == null) {
                ctx.channel().close();
                return;
            }

            channelContextUtils.addContext(tokenUserInfoDto.getUserId(), ctx.channel());
        }
    }

    /**
     * 获取token
     */
    private String getToken(String url) {
        if (StringUtils.isEmpty(url) || !url.contains("?")) {
            return null;
        }
        String[] queryParams = url.split("\\?");
        if (queryParams.length != 2)
            return null;
        String[] params = queryParams[1].split("=");
        if (params.length != 2)
            return null;
        return params[1];
    }
}
