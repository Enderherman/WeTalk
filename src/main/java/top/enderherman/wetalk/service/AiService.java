package top.enderherman.wetalk.service;

import reactor.core.publisher.Flux;

public interface AiService {

    public Flux<String> sendMsgFlow(String msg);
}
