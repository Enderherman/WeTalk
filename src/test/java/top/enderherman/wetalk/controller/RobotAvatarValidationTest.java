package top.enderherman.wetalk.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.exception.BusinessException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RobotAvatarValidationTest {
    @TempDir
    Path data;

    private ManageController controller;
    private RedisComponent redis;

    @BeforeEach
    void setUp() {
        controller = new ManageController();
        redis = mock(RedisComponent.class);
        AppConfig appConfig = new AppConfig();
        appConfig.setProjectFolder(data.toString());
        ReflectionTestUtils.setField(controller, "appConfig", appConfig);
        ReflectionTestUtils.setField(controller, "redisComponent", redis);
    }

    @Test
    void acceptsSupportedRobotAvatarAndCoverAndKeepsTheRobotIdFixed() throws Exception {
        byte[] avatar = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1 };
        byte[] cover = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 2 };
        MockMultipartFile avatarFile = new MockMultipartFile("robotAvatarFile", "robot.jpg", "image/jpeg", avatar);
        MockMultipartFile coverFile = new MockMultipartFile("robotAvatarCoverFile", "cover.jpg", "image/jpeg", cover);
        SysSettingDto settings = new SysSettingDto();
        settings.setRobotUid("Uattacker-chosen");

        controller.saveSystemSetting(settings, avatarFile, coverFile);

        Path avatarPath = data.resolve("file/avatar/Urobot.png");
        assertArrayEquals(avatar, Files.readAllBytes(avatarPath));
        assertArrayEquals(cover, Files.readAllBytes(Path.of(avatarPath + "_cover.png")));
        assertEquals(Constants.ROBOT_UID, settings.getRobotUid());
        verify(redis).saveSysSetting(settings);
    }

    @Test
    void rejectsUnsupportedAvatarBeforeSavingSettingsOrWritingFiles() {
        MockMultipartFile avatarFile = new MockMultipartFile(
                "robotAvatarFile", "robot.exe", "application/octet-stream", new byte[] { 1 });

        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(new SysSettingDto(), avatarFile, null));

        verifyNoInteractions(redis);
        assertFalse(Files.exists(data.resolve("file/avatar/Urobot.png")));
    }

    @Test
    void rejectsMismatchedContentTypeInvalidSignatureAndOversizedFiles() {
        MockMultipartFile mismatch = new MockMultipartFile(
                "robotAvatarFile", "robot.png", "image/jpeg", new byte[] { 1 });
        MockMultipartFile invalidSignature = new MockMultipartFile(
                "robotAvatarFile", "robot.png", "image/png", new byte[] { 1, 2, 3, 4 });
        MockMultipartFile oversizedAvatar = new MockMultipartFile(
                "robotAvatarFile", "robot.png", "image/png", new byte[10 * 1024 * 1024 + 1]);
        MockMultipartFile oversizedCover = new MockMultipartFile(
                "robotAvatarCoverFile", "cover.png", "image/png", new byte[10 * 1024 * 1024 + 1]);
        MockMultipartFile validAvatar = new MockMultipartFile(
                "robotAvatarFile", "robot.png", "image/png", new byte[] { 1 });
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(new SysSettingDto(), mismatch, null));
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(new SysSettingDto(), invalidSignature, null));
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(new SysSettingDto(), oversizedAvatar, null));
        assertThrows(BusinessException.class, () -> controller.saveSystemSetting(new SysSettingDto(), validAvatar, oversizedCover));
        verifyNoInteractions(redis);
    }

    @Test
    void allowsUpdatingOnlyTheRobotCover() throws Exception {
        byte[] cover = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 3 };
        MockMultipartFile coverFile = new MockMultipartFile("robotAvatarCoverFile", "cover.jpg", "image/jpeg", cover);
        SysSettingDto settings = new SysSettingDto();

        controller.saveSystemSetting(settings, null, coverFile);

        Path coverPath = data.resolve("file/avatar/Urobot.png_cover.png");
        assertArrayEquals(cover, Files.readAllBytes(coverPath));
        verify(redis).saveSysSetting(settings);
    }
}
