package top.enderherman.wetalk.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.webSocket.netty.NettyWebSocketStart;

@Component("websocketHealthIndicator")
public class WebSocketHealthIndicator implements HealthIndicator {
    private final NettyWebSocketStart server;

    public WebSocketHealthIndicator(NettyWebSocketStart server) {
        this.server = server;
    }

    @Override
    public Health health() {
        return server.isRunning() ? Health.up().build() : Health.down().build();
    }
}
