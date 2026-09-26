package top.enderherman.wetalk.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig<V> {
    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;
    @Value("${spring.data.redis.port:6379}")
    private Integer redisPort;
    @Value("${spring.data.redis.password:}")
    private String password;
    @Value("${spring.data.redis.username:}")
    private String username;
    @Value("${spring.data.redis.database:0}")
    private int database;
    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean ssl;

    Config buildConfig() {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress((ssl ? "rediss://" : "redis://") + redisHost + ":" + redisPort)
                .setDatabase(database);
        if (password != null && !password.isEmpty()) server.setPassword(password);
        if (username != null && !username.isEmpty()) server.setUsername(username);
        return config;
    }

    @Bean(name = "redissonClient", destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        return Redisson.create(buildConfig());
    }

    @Bean("redisTemplate")
    public RedisTemplate<String, V> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, V> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.json());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(RedisSerializer.json());
        template.afterPropertiesSet();
        return template;
    }
}
