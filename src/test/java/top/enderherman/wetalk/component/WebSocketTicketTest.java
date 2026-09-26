package top.enderherman.wetalk.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.utils.RedisUtils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketTicketTest {
    private RedisComponent component;
    private RedisUtils<Object> redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        component = new RedisComponent();
        redis = mock(RedisUtils.class);
        ReflectionTestUtils.setField(component, "redisUtils", redis);
    }

    @Test
    void ticketIsStoredWithShortLifetimeAndConsumedAtomically() {
        TokenUserInfoDto user = TokenUserInfoDto.builder().userId("U100").token("legacy-token").build();
        when(redis.getAndDelete(Constants.REDIS_KEY_WS_TICKET + "ticket-value")).thenReturn(user);

        component.saveWebSocketTicket("ticket-value", user);
        assertSame(user, component.consumeWebSocketTicket("ticket-value"));

        verify(redis).setEx(Constants.REDIS_KEY_WS_TICKET + "ticket-value", user, 60);
        verify(redis).getAndDelete(Constants.REDIS_KEY_WS_TICKET + "ticket-value");
    }

    @Test
    void emptyTicketCannotBeConsumed() {
        assertNull(component.consumeWebSocketTicket(""));
    }
}
