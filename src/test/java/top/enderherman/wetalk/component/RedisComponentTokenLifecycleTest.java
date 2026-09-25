package top.enderherman.wetalk.component;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.utils.RedisUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RedisComponentTokenLifecycleTest {

    @Test
    void clearingUserTokenMakesTheTokenUnreadableAndRemovesTheUserMapping() {
        MemoryRedisUtils redis = new MemoryRedisUtils();
        RedisComponent component = new RedisComponent();
        ReflectionTestUtils.setField(component, "redisUtils", redis);

        String userId = "test-user";
        String token = "test-token";
        TokenUserInfoDto dto = TokenUserInfoDto.builder()
                .userId(userId)
                .token(token)
                .build();

        component.saveTokenUserInfoDto(dto);
        assertNotNull(component.getTokenUserInfoDto(token));
        assertNotNull(redis.get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId));

        component.clearTokenUserInfoDto(userId);

        assertNull(component.getTokenUserInfoDto(token));
        assertNull(redis.get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId));
    }

    private static class MemoryRedisUtils extends RedisUtils<Object> {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Object get(String key) {
            return values.get(key);
        }

        @Override
        public boolean setEx(String key, Object value, long time) {
            values.put(key, value);
            return true;
        }

        @Override
        public void delete(String... keys) {
            for (String key : keys) {
                values.remove(key);
            }
        }
    }
}
