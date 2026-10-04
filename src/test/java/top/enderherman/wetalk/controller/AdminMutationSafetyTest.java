package top.enderherman.wetalk.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.service.UserInfoService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminMutationSafetyTest {
    private ManageController controller;
    private UserInfoService users;
    private RedisComponent redis;

    @BeforeEach
    void setUp() {
        controller = new ManageController() {
            @Override protected TokenUserInfoDto getTokenUserDto(HttpServletRequest request) {
                return TokenUserInfoDto.builder().userId("Uadmin").admin(true).build();
            }
        };
        users = mock(UserInfoService.class);
        redis = mock(RedisComponent.class);
        ReflectionTestUtils.setField(controller, "userInfoService", users);
        ReflectionTestUtils.setField(controller, "redisComponent", redis);
    }

    @Test
    void administratorCannotDisableOrForceOffTheirOwnAccountThroughDirectApi() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(BusinessException.class, () -> controller.updateUserStatus(request, "Uadmin", 0));
        assertThrows(BusinessException.class, () -> controller.forcedOffOnline(request, "Uadmin"));
        verifyNoInteractions(users);
    }

    @Test
    void administratorCanStillManageAnotherAccount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        controller.updateUserStatus(request, "U200", 0);
        controller.forcedOffOnline(request, "U200");
        verify(users).updateUserStatus("U200", 0);
        verify(users).forcedOffOnline("U200");
    }

    @Test
    void invalidQuotaDoesNotReplaceSystemConfiguration() {
        SysSettingDto settings = new SysSettingDto();
        settings.setMaxGroupCount(0);
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(settings, null, null));
        settings.setMaxGroupCount(1);
        settings.setMaxFileSize(-1);
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(settings, null, null));
        settings.setMaxFileSize(null);
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(settings, null, null));
        verifyNoInteractions(redis);
    }

    @Test
    void robotNameAndWelcomeMustFitSharedClientContract() {
        SysSettingDto settings = new SysSettingDto();
        settings.setRobotNickName("x".repeat(21));
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(settings, null, null));
        settings.setRobotNickName("Robot");
        settings.setRobotWelcome("x".repeat(301));
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(settings, null, null));
        settings.setRobotWelcome(" ");
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(settings, null, null));
        verifyNoInteractions(redis);
    }

    @Test
    void validBoundarySettingsAreSavedWithNormalizedRobotName() throws Exception {
        SysSettingDto settings = new SysSettingDto();
        settings.setRobotNickName(" " + "x".repeat(20) + " ");
        settings.setRobotWelcome("x".repeat(300));
        controller.saveSystemSetting(settings, null, null);
        assertEquals(20, settings.getRobotNickName().length());
        verify(redis).saveSysSetting(settings);
    }
}
