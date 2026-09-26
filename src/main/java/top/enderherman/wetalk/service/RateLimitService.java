package top.enderherman.wetalk.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class RateLimitService {

    private static final String RATE_LIMIT_KEY_PREFIX = "wetalk:rate:";
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>();

    static {
        INCREMENT_WITH_TTL.setScriptText(
                "local count = redis.call('INCR', KEYS[1]); " +
                        "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; " +
                        "return count");
        INCREMENT_WITH_TTL.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** Atomically records an attempt and rejects requests above the configured fixed window. */
    public void enforce(String scope, String identity, long maxAttempts, long windowSeconds) {
        if (scope == null || !scope.matches("[a-z0-9-]{1,40}")
                || maxAttempts < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Invalid rate limit policy.");
        }

        Long count;
        try {
            count = stringRedisTemplate.execute(INCREMENT_WITH_TTL,
                    List.of(RATE_LIMIT_KEY_PREFIX + scope + ":" + hash(identity)),
                    Long.toString(windowSeconds));
        } catch (RuntimeException error) {
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
        if (count == null) throw new BusinessException(ResponseCodeEnum.CODE_500);
        if (count > maxAttempts) throw new BusinessException(ResponseCodeEnum.CODE_429);
    }

    private String hash(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((identity == null ? "unknown" : identity.trim().toLowerCase(Locale.ROOT))
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }
}
