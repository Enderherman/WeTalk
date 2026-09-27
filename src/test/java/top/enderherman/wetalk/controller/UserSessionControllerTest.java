package top.enderherman.wetalk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.common.BaseResponse;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.vo.UserSessionVO;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserSessionControllerTest {
    private TestController controller;
    private RedisComponent redis;
    private ChannelContextUtils channels;
    private TokenUserInfoDto current;

    @BeforeEach
    void setUp() {
        controller = new TestController();
        redis = mock(RedisComponent.class);
        channels = mock(ChannelContextUtils.class);
        ReflectionTestUtils.setField(controller, "redisComponent", redis);
        ReflectionTestUtils.setField(controller, "channelContextUtils", channels);
        ReflectionTestUtils.setField(controller, "appConfig", new AppConfig());
        current = TokenUserInfoDto.builder()
                .userId("U100")
                .token("secret-current-token")
                .sessionId("session-current")
                .deviceName("Chrome · Windows")
                .createdAt(100L)
                .lastActiveAt(300L)
                .build();
    }

    @Test
    void sessionListMarksCurrentDeviceAndNeverSerializesCredentials() throws Exception {
        TokenUserInfoDto other = TokenUserInfoDto.builder()
                .userId("U100")
                .token("secret-other-token")
                .sessionId("session-other")
                .deviceName("Safari · iOS")
                .createdAt(100L)
                .lastActiveAt(200L)
                .build();
        when(redis.getUserSessions("U100")).thenReturn(List.of(current, other));

        BaseResponse<List<UserSessionVO>> response = controller.listSessions(request());

        assertTrue(response.getData().get(0).isCurrent());
        assertFalse(response.getData().get(1).isCurrent());
        String json = new ObjectMapper().writeValueAsString(response);
        assertFalse(json.contains("secret-current-token"));
        assertFalse(json.contains("secret-other-token"));
        assertFalse(json.contains("\"token\""));
        assertEquals("Chrome · Windows", response.getData().get(0).getDeviceName());
    }

    @Test
    void revokingOneSessionLeavesCurrentCookieAndOtherSessionsAlone() {
        TokenUserInfoDto other = TokenUserInfoDto.builder()
                .userId("U100").token("other-token").sessionId("session-other").build();
        when(redis.getTokenUserInfoDtoBySessionId("session-other")).thenReturn(other);
        when(redis.removeUserSession("U100", "session-other")).thenReturn(other);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.revokeSession(request(), response, "session-other");

        verify(redis).removeUserSession("U100", "session-other");
        verify(channels).closeSession("U100", "session-other");
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void logoutOtherSessionsKeepsCurrentSessionAndReportsRevokedCount() {
        TokenUserInfoDto one = TokenUserInfoDto.builder().userId("U100").token("one").sessionId("session-one").build();
        TokenUserInfoDto two = TokenUserInfoDto.builder().userId("U100").token("two").sessionId("session-two").build();
        when(redis.removeOtherUserSessions("U100", "session-current")).thenReturn(List.of(one, two));

        BaseResponse<?> response = controller.revokeOtherSessions(request());

        assertEquals(Map.of("revokedCount", 2), response.getData());
        verify(channels).closeSession("U100", "session-one");
        verify(channels).closeSession("U100", "session-two");
        verify(channels, never()).closeSession("U100", "session-current");
    }

    @Test
    void cannotRevokeAnotherUsersSession() {
        TokenUserInfoDto otherUser = TokenUserInfoDto.builder()
                .userId("U200").token("other").sessionId("session-other").build();
        when(redis.getTokenUserInfoDtoBySessionId("session-other")).thenReturn(otherUser);

        assertThrows(RuntimeException.class,
                () -> controller.revokeSession(request(), new MockHttpServletResponse(), "session-other"));
        verify(redis, never()).removeUserSession(anyString(), anyString());
        verifyNoInteractions(channels);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(Constants.WEB_SESSION_COOKIE, current.getToken()));
        return request;
    }

    private class TestController extends UserInfoController {
        @Override
        protected TokenUserInfoDto getTokenUserDto(jakarta.servlet.http.HttpServletRequest request) {
            return current;
        }
    }
}
