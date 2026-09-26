package top.enderherman.wetalk.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.service.AppUpdateService;
import top.enderherman.wetalk.utils.RedisUtils;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUpdateControllerSecurityTest {

    @Mock
    private AppUpdateService appUpdateService;

    @Mock
    private RedisUtils<?> redisUtils;

    @InjectMocks
    private AppUpdateController controller;

    @Test
    void checksGrayReleaseWithAuthenticatedUserInsteadOfCallerSuppliedUid() {
        TokenUserInfoDto authenticatedUser = new TokenUserInfoDto();
        authenticatedUser.setUserId("U100");
        doReturn(authenticatedUser).when(redisUtils).get(Constants.REDIS_KEY_WS_TOKEN + "session-token");
        when(appUpdateService.getLatestUpdate("0.1.0", "U100")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("token", "session-token");

        controller.checkUpdate(request, "0.1.0", "U999");

        verify(appUpdateService).getLatestUpdate("0.1.0", "U100");
    }
}
