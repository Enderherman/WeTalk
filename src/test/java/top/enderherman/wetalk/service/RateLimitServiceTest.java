package top.enderherman.wetalk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import top.enderherman.wetalk.exception.BusinessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RateLimitService rateLimitService;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void recordsOnlyAHashOfTheIdentityAndAllowsRequestsWithinTheLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        rateLimitService.enforce("account-login", " Student@Example.com ", 10, 600);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any());
        assertTrue(keys.getValue().get(0).startsWith("wetalk:rate:account-login:"));
        assertFalse(keys.getValue().get(0).contains("student@example.com"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectsRequestsAfterTheLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(6L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> rateLimitService.enforce("account-login", "user@example.invalid", 5, 600));
        assertEquals(429, error.getCode());
    }
}
