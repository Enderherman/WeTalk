package top.enderherman.wetalk.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.utils.AuthTokenResolver;
import top.enderherman.wetalk.utils.WebAuthCookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAuthCookieTest {
    @Test
    void resolvesHttpOnlyCookieWhenLegacyHeaderIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(Constants.WEB_SESSION_COOKIE, "cookie-token"));

        assertEquals("cookie-token", AuthTokenResolver.resolve(request));
    }

    @Test
    void legacyTokenHeaderTakesPrecedenceOverCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(Constants.WEB_SESSION_COOKIE, "cookie-token"));
        request.addHeader("token", "desktop-token");

        assertEquals("desktop-token", AuthTokenResolver.resolve(request));
    }

    @Test
    void sessionCookieIsHttpOnlyStrictAndHasTheServerTokenLifetime() {
        String header = WebAuthCookie.session("token-value", true).toString();

        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=Strict"));
        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("Max-Age=172800"));
        assertFalse(header.toLowerCase().contains("domain="));
    }

    @Test
    void clearCookieUsesTheSameScopeAndExpiresImmediately() {
        String header = WebAuthCookie.clear(false).toString();

        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict"));
        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("Max-Age=0"));
    }
}
