package top.enderherman.wetalk.service.impl;


import jodd.util.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.enderherman.wetalk.service.AiService;

@Service("aiService")
public class AiServiceImpl implements AiService {

    private final ChatClient chatClient;

    public AiServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Flux<String> sendMsgFlow(String msg) {
        if (StringUtil.isEmpty(msg)) {
            msg = "你是谁?";
        }
        return this.chatClient
                .prompt()
                .user(msg)
                .stream()
                .content();
    }
}
