package top.enderherman.wetalk.config;


import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Slf4j
@Configuration
public class RedisConfig<V> {


    @Value("${spring.data.redis.host:}")
    private String redisHost;

    @Value("${spring.data.redis.port:}")
    private Integer redisPort;

    @Value("${spring.redis.password:}")
    private String password;

    @Bean(name="redissonClient", destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        log.info("redissonClient start:{},{}", redisHost,redisPort);
        try {
            Config config = new Config();
            config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
            return Redisson.create(config);
        } catch (Exception e) {
            log.error("Redis 配置错误，请检查 Redis 配置",e);
        }
        return null;
    }


    @Bean("redisTemplate")
    public RedisTemplate<String, V> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, V> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        // 设置 key 的序列化方式
        template.setKeySerializer(RedisSerializer.string());
        // 设置 value 的序列化方式
        template.setValueSerializer(RedisSerializer.json());
        // 设置 hash key 的序列化方式
        template.setHashKeySerializer(RedisSerializer.string());
        // 设置 hash value 的序列化方式
        template.setHashValueSerializer(RedisSerializer.json());
        template.afterPropertiesSet();
        return template;
    }
}
