package top.enderherman.wetalk.webSocket;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.webSocket.netty.WebSocketHandler;
import static org.junit.jupiter.api.Assertions.*;
class WebSocketTokenTest {
 WebSocketHandler handler=new WebSocketHandler();
 String token(String uri) {return ReflectionTestUtils.invokeMethod(handler,"getToken",uri);}
 @Test void acceptsNamedTokenAmongOtherQueryParameters() {assertEquals("abc",token("/ws?client=web&token=abc&version=1"));}
 @Test void cannotUseAnArbitraryQueryParameterAsToken() {assertNull(token("/ws?other=abc"));}
 @Test void duplicateTokensAreRejected() {assertNull(token("/ws?token=a&token=b"));}
 @Test void encodedTokenIsDecoded() {assertEquals("a=b",token("/ws?token=a%3Db"));}
}
