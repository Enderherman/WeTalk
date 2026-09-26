package top.enderherman.wetalk.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import top.enderherman.wetalk.constants.Constants;

public final class AuthTokenResolver {
    private AuthTokenResolver() {}

    public static String resolve(HttpServletRequest request) {
        String headerToken = request.getHeader("token");
        if (headerToken != null && !headerToken.isBlank()) return headerToken;

        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (Constants.WEB_SESSION_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
