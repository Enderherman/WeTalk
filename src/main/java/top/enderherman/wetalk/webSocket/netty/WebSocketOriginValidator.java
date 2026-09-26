package top.enderherman.wetalk.webSocket.netty;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WebSocketOriginValidator {

    private final Set<String> allowedOrigins;

    public WebSocketOriginValidator(@Value("${wetalk.web.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(WebSocketOriginValidator::normalizeOrigin)
                .filter(origin -> origin != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAllowed(String origin, String requestUri) {
        if (origin == null || origin.isBlank()) return true;
        if ("null".equalsIgnoreCase(origin)) return hasLegacyToken(requestUri);
        String normalized = normalizeOrigin(origin);
        return normalized != null && allowedOrigins.contains(normalized);
    }

    private boolean hasLegacyToken(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) return false;
        try {
            var parameters = new QueryStringDecoder(requestUri).parameters();
            var tokens = parameters.get("token");
            return tokens != null && tokens.size() == 1 && !tokens.get(0).isBlank() && !parameters.containsKey("ticket");
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String normalizeOrigin(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
            host = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port < 0) port = "https".equals(scheme) ? 443 : 80;
            return scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
