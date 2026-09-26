package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.UserInfoMapper;
import top.enderherman.wetalk.service.impl.UserInfoServiceImpl;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserProfileImageValidationTest {
    @TempDir
    Path data;

    private UserInfoServiceImpl service;
    private UserInfoMapper<UserInfo, ?> users;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new UserInfoServiceImpl();
        users = mock(UserInfoMapper.class);
        AppConfig config = new AppConfig();
        config.setProjectFolder(data.toString());
        ReflectionTestUtils.setField(service, "userInfoMapper", users);
        ReflectionTestUtils.setField(service, "appConfig", config);

        UserInfo existing = new UserInfo();
        existing.setUserId("U100");
        existing.setNickName("Student");
        when(users.selectByUserId("U100")).thenReturn(existing);
        when(users.updateByUserId(any(UserInfo.class), eq("U100"))).thenReturn(1);
    }

    private UserInfo profileUpdate() {
        UserInfo update = new UserInfo();
        update.setUserId("U100");
        update.setNickName("Student");
        update.setSex(1);
        return update;
    }

    @Test
    void acceptsExistingElectronJpegAvatarAndOptionalCoverUpload() throws Exception {
        byte[] avatar = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1 };
        byte[] cover = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 2 };
        MockMultipartFile avatarFile = new MockMultipartFile("avatarFile", "avatar.jpg", "image/jpeg", avatar);
        MockMultipartFile coverFile = new MockMultipartFile("coverFile", "cover.jpg", "image/jpeg", cover);

        service.updateUserInfo(profileUpdate(), avatarFile, coverFile);

        Path avatarPath = data.resolve("file/avatar/U100.png");
        assertArrayEquals(avatar, Files.readAllBytes(avatarPath));
        assertArrayEquals(cover, Files.readAllBytes(Path.of(avatarPath + "_cover.png")));
    }

    @Test
    void rejectsNonImageExtensionsAndMimeTypes() {
        MockMultipartFile invalid = new MockMultipartFile("avatarFile", "avatar.exe", "application/octet-stream", new byte[] { 1 });

        assertThrows(BusinessException.class, () -> service.updateUserInfo(profileUpdate(), invalid, null));
    }

    @Test
    void rejectsProfileImagesAboveTenMebibytes() {
        byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile invalid = new MockMultipartFile("avatarFile", "avatar.png", "image/png", tooLarge);

        assertThrows(BusinessException.class, () -> service.updateUserInfo(profileUpdate(), invalid, null));
    }
}
