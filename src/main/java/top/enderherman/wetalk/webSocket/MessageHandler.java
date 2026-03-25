package top.enderherman.wetalk.webSocket;


import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * 消息处理器
 */
@Slf4j
@Component("messageHandler")
public class MessageHandler {

    private static final String MESSAGE_TOPIC = "message.topic";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @PostConstruct
    public void lisMessageSend() {
        RTopic rTopic = redissonClient.getTopic(MESSAGE_TOPIC);
        rTopic.addListener(MessageSendDTO.class, (MessageSendDTO, sendDto) -> {
            log.info("收到广播消息:{}", JSONUtil.toJsonStr(sendDto));
            channelContextUtils.sendMessage(sendDto);
        });
    }

    public void sendMessage(MessageSendDTO<?> messageSendDTO) {
        RTopic rTopic = redissonClient.getTopic(MESSAGE_TOPIC);
        rTopic.publish(messageSendDTO);
    }

}
