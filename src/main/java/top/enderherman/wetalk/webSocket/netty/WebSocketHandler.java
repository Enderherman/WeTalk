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
        Attribute<String> attribute = channel.attr(ChannelContextUtils.USER_ID);
        String userId = attribute.get();
        //log.info("收到用户: {} 的消息:{}", userId, textWebSocketFrame.text());
        if (StringUtils.isEmpty(userId)) {
            ctx.close();
            return;
        }
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
            TokenUserInfoDto tokenUserInfoDto = authenticate(url);
            if (tokenUserInfoDto == null) {
                log.warn("WebSocket 握手缺少认证令牌");
                ctx.channel().close();
                return;
            }
            channelContextUtils.addContext(tokenUserInfoDto.getUserId(), ctx.channel());
        }
    }

    private TokenUserInfoDto authenticate(String url) {
        if (StringUtils.isEmpty(url)) return null;
        try {
            java.util.Map<String, java.util.List<String>> parameters =
                    new io.netty.handler.codec.http.QueryStringDecoder(url).parameters();
            java.util.List<String> tickets = parameters.get("ticket");
            java.util.List<String> tokens = parameters.get("token");
            if (tickets != null) {
                if (tickets.size() != 1 || tokens != null) return null;
                return redisComponent.consumeWebSocketTicket(tickets.get(0));
            }
            String token = getToken(url);
            return StringUtils.isEmpty(token) ? null : redisComponent.getTokenUserInfoDto(token);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getToken(String url) {
        if (StringUtils.isEmpty(url)) return null;
        try {
            java.util.List<String> tokens = new io.netty.handler.codec.http.QueryStringDecoder(url)
                    .parameters().get("token");
            return tokens != null && tokens.size() == 1 ? tokens.get(0) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
