package top.enderherman.wetalk.webSocket.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChannelHandler.Sharable
public class HeartBeatHandler extends ChannelDuplexHandler {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                Channel channel = ctx.channel();
                Attribute<String> attribute = channel.attr(ChannelContextUtils.USER_ID);
                String userId = attribute.get();
                log.info("用户: {} 心跳超时", userId);
                ctx.close();
            } else if (event.state() == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush("heart");
            }


        }
    }
}
