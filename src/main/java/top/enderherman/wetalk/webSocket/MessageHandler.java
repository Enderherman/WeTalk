package top.enderherman.wetalk.webSocket;


import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * 消息处理器
 */
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
            channelContextUtils.sendMessage(sendDto);
        });
    }

    public void sendMessage(MessageSendDTO<?> messageSendDTO) {
        RTopic rTopic = redissonClient.getTopic(MESSAGE_TOPIC);
        rTopic.publish(messageSendDTO);
    }

}
