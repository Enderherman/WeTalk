package top.enderherman.wetalk.component;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.utils.RedisUtils;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertNotNull(redis.get(Constants.REDIS_KEY_WS_SESSION + dto.getSessionId()));
        assertEquals(List.of(dto.getSessionId()), redis.getQueueList(Constants.REDIS_KEY_WS_SESSIONS_USER + userId));

        component.clearTokenUserInfoDto(userId);

        assertNull(component.getTokenUserInfoDto(token));
        assertNull(redis.get(Constants.REDIS_KEY_WS_SESSION + dto.getSessionId()));
        assertNull(redis.getQueueList(Constants.REDIS_KEY_WS_SESSIONS_USER + userId));
    }

    @Test
    void separateLoginTokensRemainValidAndCanBeRevokedIndividually() {
        MemoryRedisUtils redis = new MemoryRedisUtils();
        RedisComponent component = new RedisComponent();
        ReflectionTestUtils.setField(component, "redisUtils", redis);
        TokenUserInfoDto first = TokenUserInfoDto.builder().userId("test-user").token("old").build();
        TokenUserInfoDto second = TokenUserInfoDto.builder().userId("test-user").token("new").build();
        component.saveTokenUserInfoDto(first);
        component.saveTokenUserInfoDto(second);
        assertNotNull(component.getTokenUserInfoDto("old"));
        assertNotNull(component.getTokenUserInfoDto("new"));
        assertEquals(2, component.getUserSessions("test-user").size());

        assertNotNull(component.removeUserSession("test-user", first.getSessionId()));
        assertNull(component.getTokenUserInfoDto("old"));
        assertNotNull(component.getTokenUserInfoDto("new"));
    }

    @Test
    void anAlreadyIssuedTicketCannotAuthenticateARevokedSession() {
        MemoryRedisUtils redis = new MemoryRedisUtils();
        RedisComponent component = new RedisComponent();
        ReflectionTestUtils.setField(component, "redisUtils", redis);
        TokenUserInfoDto session = TokenUserInfoDto.builder().userId("test-user").token("token").build();
        component.saveTokenUserInfoDto(session);
        component.saveWebSocketTicket("ticket", session);

        component.removeUserSession("test-user", session.getSessionId());

        assertNull(component.consumeWebSocketTicket("ticket"));
    }

    @Test
    void legacySingleTokenMappingMigratesWithoutRevokingTheCredential() {
        MemoryRedisUtils redis = new MemoryRedisUtils();
        RedisComponent component = new RedisComponent();
        ReflectionTestUtils.setField(component, "redisUtils", redis);
        TokenUserInfoDto legacy = TokenUserInfoDto.builder().userId("test-user").token("legacy-token").build();
        redis.setEx(Constants.REDIS_KEY_WS_TOKEN + "legacy-token", legacy, 3600);
        redis.setEx(Constants.REDIS_KEY_WS_TOKEN_USERID + "test-user", "legacy-token", 3600);

        List<TokenUserInfoDto> sessions = component.getUserSessions("test-user");

        assertEquals(1, sessions.size());
        assertNotNull(sessions.get(0).getSessionId());
        assertNotNull(component.getTokenUserInfoDto("legacy-token"));
        assertNotNull(redis.get(Constants.REDIS_KEY_WS_SESSION + sessions.get(0).getSessionId()));
    }

    private static class MemoryRedisUtils extends RedisUtils<Object> {
        private final Map<String, Object> values = new HashMap<>();
        private final Map<String, List<Object>> lists = new HashMap<>();

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
        public Object getAndDelete(String key) {
            return values.remove(key);
        }

        @Override
        public boolean listPush(String key, Object value, long time) {
            lists.computeIfAbsent(key, ignored -> new ArrayList<>()).add(0, value);
            return true;
        }

        @Override
        public List<Object> getQueueList(String key) {
            List<Object> result = lists.get(key);
            return result == null ? null : new ArrayList<>(result);
        }

        @Override
        public long listRemove(String key, Object value) {
            List<Object> result = lists.get(key);
            if (result == null) return 0;
            int size = result.size();
            result.removeIf(existing -> Objects.equals(existing, value));
            if (result.isEmpty()) lists.remove(key);
            return size - (result == null ? 0 : result.size());
        }

        @Override
        public void delete(String... keys) {
            for (String key : keys) {
                values.remove(key);
                lists.remove(key);
            }
        }
    }
}
