package top.enderherman.wetalk.utils;

import org.springframework.http.ResponseCookie;
import top.enderherman.wetalk.constants.Constants;

import java.time.Duration;

public final class WebAuthCookie {
    private WebAuthCookie() {}

    public static ResponseCookie session(String token, boolean secure) {
        return ResponseCookie.from(Constants.WEB_SESSION_COOKIE, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofSeconds(Constants.REDIS_KEY_EXPIRES_DAY * 2L))
                .build();
    }

    public static ResponseCookie clear(boolean secure) {
        return ResponseCookie.from(Constants.WEB_SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
