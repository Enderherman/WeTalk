package top.enderherman.wetalk.service;

import reactor.core.publisher.Flux;

public interface AiService {

    default boolean isEnabled() { return true; }

    public Flux<String> sendMsgFlow(String msg);
}
