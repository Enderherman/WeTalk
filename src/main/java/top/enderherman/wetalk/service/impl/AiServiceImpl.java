package top.enderherman.wetalk.service.impl;


import jodd.util.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.enderherman.wetalk.service.AiService;

@Service("aiService")
public class AiServiceImpl implements AiService {

    private final org.springframework.beans.factory.ObjectProvider<ChatClient> chatClient;

    public AiServiceImpl(org.springframework.beans.factory.ObjectProvider<ChatClient> chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public boolean isEnabled() {
        return chatClient.getIfAvailable() != null;
    }

    @Override
    public Flux<String> sendMsgFlow(String msg) {
        ChatClient client = chatClient.getIfAvailable();
        if (client == null) return Flux.error(new IllegalStateException("AI service is disabled"));
        if (StringUtil.isEmpty(msg)) {
            msg = "你是谁?";
        }
        return client
                .prompt()
                .user(msg)
                .stream()
                .content();
    }
}
