package top.enderherman.wetalk;

import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.utils.RedisUtils;
import top.enderherman.wetalk.webSocket.netty.NettyWebSocketStart;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class InitRun implements ApplicationRunner {
    @Resource private DataSource dataSource;
    @Resource private RedisUtils<?> redisUtils;
    @Resource private NettyWebSocketStart nettyWebSocketStart;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) throw new IllegalStateException("Database is not ready");
            redisUtils.get("wetalk:startup:probe");
        } catch (Exception e) {
            throw new IllegalStateException("Database/Redis startup check failed", e);
        }
        new Thread(nettyWebSocketStart, "wetalk-websocket").start();
    }
}
