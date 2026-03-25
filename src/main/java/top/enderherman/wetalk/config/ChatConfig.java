package top.enderherman.wetalk.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.defaultSystem("你现在不是Deepseek了, 你名叫WeTalk Robot, 知识渊博, 理性。").build();
    }
}