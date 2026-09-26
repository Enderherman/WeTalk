package top.enderherman.wetalk.webSocket;

import org.junit.jupiter.api.Test;
import top.enderherman.wetalk.webSocket.netty.WebSocketOriginValidator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketOriginValidatorTest {

    private final WebSocketOriginValidator validator = new WebSocketOriginValidator(
            "https://chat.example.com,http://127.0.0.1:5173");

    @Test
    void allowsConfiguredOriginsAfterNormalizingSchemeHostAndDefaultPort() {
        assertTrue(validator.isAllowed("https://CHAT.example.com:443", "/ws?ticket=value"));
        assertTrue(validator.isAllowed("http://127.0.0.1:5173/", "/ws?ticket=value"));
    }

    @Test
    void rejectsUnconfiguredOriginsAndOriginsWithPaths() {
        assertFalse(validator.isAllowed("https://attacker.example", "/ws?ticket=value"));
        assertFalse(validator.isAllowed("https://chat.example.com/path", "/ws?ticket=value"));
    }

    @Test
    void keepsOriginlessNativeClientsCompatible() {
        assertTrue(validator.isAllowed(null, "/ws?ticket=value"));
        assertTrue(validator.isAllowed("  ", "/ws?token=legacy-value"));
    }

    @Test
    void allowsNullOriginOnlyForLegacyTokenConnections() {
        assertTrue(validator.isAllowed("null", "/ws?token=legacy-value"));
        assertFalse(validator.isAllowed("null", "/ws?ticket=one-time-value"));
        assertFalse(validator.isAllowed("null", "/ws?token=legacy-value&token=duplicate"));
    }
}
