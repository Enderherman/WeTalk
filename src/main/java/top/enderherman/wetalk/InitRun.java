package top.enderherman.wetalk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.utils.RedisUtils;
import top.enderherman.wetalk.webSocket.netty.NettyWebSocketStart;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;

@Slf4j
@Component("initRun")
public class InitRun implements ApplicationRunner {

    @Resource
    private DataSource dataSource;

    @Resource
    private RedisUtils<?> redisUtils;

    @Resource
    private NettyWebSocketStart nettyWebSocketStart;

    @Override
    public void run(ApplicationArguments args) {
        try {
            dataSource.getConnection();
            redisUtils.get("test");
            new Thread(nettyWebSocketStart).start();

        } catch (SQLException e) {
            log.error("数据库配置异常",e);
        } catch (RedisConnectionFailureException e) {
            log.error("redis配置异常",e);
        } catch (Exception e) {
            log.error("服务启动失败");
        }

    }
}
