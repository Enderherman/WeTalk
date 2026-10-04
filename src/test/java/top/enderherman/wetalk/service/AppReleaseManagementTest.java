package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.po.AppUpdate;
import top.enderherman.wetalk.entity.query.AppUpdateQuery;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.AppUpdateMapper;
import top.enderherman.wetalk.service.impl.AppUpdateServiceImpl;
import top.enderherman.wetalk.utils.AppVersion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppReleaseManagementTest {
    @TempDir Path data;
    private AppUpdateServiceImpl service;
    private AppUpdateMapper<AppUpdate, AppUpdateQuery> updates;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new AppUpdateServiceImpl();
        updates = mock(AppUpdateMapper.class);
        AppConfig config = new AppConfig();
        config.setProjectFolder(data.toString());
        ReflectionTestUtils.setField(service, "appUpdateMapper", updates);
        ReflectionTestUtils.setField(service, "appConfig", config);
        when(updates.selectList(any())).thenReturn(List.of());
        when(updates.insert(any())).thenAnswer(invocation -> {
            ((AppUpdate) invocation.getArgument(0)).setId(10);
            return 1;
        });
    }

    private AppUpdate release(int id, String version, int status, int fileType) {
        AppUpdate value = new AppUpdate();
        value.setId(id > 0 ? id : null);
        value.setVersion(version);
        value.setStatus(status);
        value.setFileType(fileType);
        value.setUpdateDesc("修复消息|改进文件");
        value.setOuterLink("https://example.invalid/releases");
        return value;
    }

    private Path packagePath(int id) {
        return data.resolve(Constants.FILE_FOLDER).resolve(Constants.APP_UPDATE_FILE).resolve(id + Constants.APP_EXE_SUFFIX);
    }

    @Test
    void comparesEachVersionComponentInsteadOfConcatenatedDigitsOrText() {
        assertTrue(AppVersion.compare("1.10.0", "1.9.9") > 0);
        assertTrue(AppVersion.compare("2.0.0", "1.99.99") > 0);
        assertTrue(AppVersion.compare("1.0.10", "1.0.9") > 0);
        assertEquals(0, AppVersion.compare("01.0.0", "1.0.0"));
        assertThrows(BusinessException.class, () -> AppVersion.compare("1..0", "1.0.0"));
        assertThrows(BusinessException.class, () -> AppVersion.compare("999999999.1.1", "1.0.0"));
    }

    @Test
    void createsNewerVersionAcrossMinorRolloverAndRejectsDowngrade() throws Exception {
        when(updates.selectList(any())).thenReturn(List.of(release(1, "1.9.9", 2, 1)));
        service.saveUpdate(release(0, "1.10.0", 0, 1), null);
        verify(updates).insert(argThat(value -> "1.10.0".equals(value.getVersion()) && value.getStatus() == 0));
        when(updates.selectList(any())).thenReturn(List.of(release(2, "1.10.0", 2, 1)));
        assertThrows(BusinessException.class, () -> service.saveUpdate(release(0, "1.9.10", 0, 1), null));
    }

    @Test
    void selectsHighestVisibleNumericVersionAndKeepsGrayReleasePrivate() {
        AppUpdate older = release(20, "1.9.9", 2, 1);
        AppUpdate newer = release(10, "1.10.0", 2, 1);
        AppUpdate hidden = release(30, "2.0.0", 1, 1);
        hidden.setGrayscaleUid("U98765432109");
        when(updates.selectVisibleUpdates("U12345678901")).thenReturn(List.of(older, newer, hidden, release(40, "3.0.0", 0, 1)));
        assertEquals("1.10.0", service.getLatestUpdate("1.9.9", "U12345678901").getVersion());
        assertNull(service.getLatestUpdate("1.10.0", "U12345678901"));
        when(updates.selectVisibleUpdates("U98765432109")).thenReturn(List.of(older, newer, hidden));
        assertEquals("2.0.0", service.getLatestUpdate("1.9.9", "U98765432109").getVersion());
    }

    @Test
    void refusesMissingRecordsAndEditingPublishedVersionsWithoutNullPointerErrors() {
        assertThrows(BusinessException.class, () -> service.deleteAppUpdateById(99));
        assertThrows(BusinessException.class, () -> service.postUpdate(99, 2, ""));
        assertThrows(BusinessException.class, () -> service.saveUpdate(release(99, "1.0.0", 0, 1), null));
        when(updates.selectById(9)).thenReturn(release(9, "1.0.0", 2, 1));
        assertThrows(BusinessException.class, () -> service.saveUpdate(release(9, "1.0.1", 0, 1), null));
        assertThrows(BusinessException.class, () -> service.deleteAppUpdateById(9));
        verify(updates, never()).deleteById(anyInt());
    }

    @Test
    void validatesLinkProtocolDescriptionsAndInstallerBeforeWritingRecords() {
        AppUpdate external = release(0, "1.0.0", 0, 1);
        for (String link : List.of("javascript:alert(1)", "file:///c:/Windows/app.exe", "https://user:pass@example.invalid/app")) {
            external.setOuterLink(link);
            assertThrows(BusinessException.class, () -> service.saveUpdate(external, null));
        }
        AppUpdate local = release(0, "1.0.0", 0, 0);
        assertThrows(BusinessException.class, () -> service.saveUpdate(local, null));
        assertThrows(BusinessException.class, () -> service.saveUpdate(local, new MockMultipartFile("file", "bad.txt", "text/plain", new byte[]{1})));
        assertThrows(BusinessException.class, () -> service.saveUpdate(local, new MockMultipartFile("file", "empty.exe", "application/octet-stream", new byte[0])));
        local.setUpdateDesc(" ");
        assertThrows(BusinessException.class, () -> service.saveUpdate(local, null));
        verify(updates, never()).insert(any());
    }

    @Test
    void savesInstallerAtDownloadPathAndPreservesItWhenEditingWithoutAFile() throws Exception {
        byte[] bytes = {'M', 'Z', 1, 2};
        service.saveUpdate(release(0, "1.0.0", 0, 0), new MockMultipartFile("file", "WeTalk.exe", "application/octet-stream", bytes));
        assertArrayEquals(bytes, Files.readAllBytes(packagePath(10)));
        when(updates.selectById(10)).thenReturn(release(10, "1.0.0", 0, 0));
        service.saveUpdate(release(10, "1.0.1", 0, 0), null);
        assertArrayEquals(bytes, Files.readAllBytes(packagePath(10)));
        verify(updates).updateById(argThat(value -> "1.0.1".equals(value.getVersion())), eq(10));
    }

    @Test
    void failedUploadLeavesExistingPackageAndDatabaseUntouched() throws Exception {
        Files.createDirectories(packagePath(9).getParent());
        Files.writeString(packagePath(9), "original installer");
        when(updates.selectById(9)).thenReturn(release(9, "1.0.0", 0, 0));
        MockMultipartFile file = new MockMultipartFile("file", "WeTalk.exe", "application/octet-stream", new byte[]{1}) {
            @Override public void transferTo(Path destination) throws IOException {
                Files.writeString(destination, "partial");
                throw new IOException("interrupted");
            }
        };
        assertThrows(IOException.class, () -> service.saveUpdate(release(9, "1.0.1", 0, 0), file));
        assertEquals("original installer", Files.readString(packagePath(9)));
        verify(updates, never()).updateById(any(), anyInt());
        try (var files = Files.list(packagePath(9).getParent())) { assertEquals(1, files.count()); }
    }

    @Test
    void publishesOnlyValidArtifactsAndNormalizesGrayUsersAndWithdrawals() {
        when(updates.selectById(9)).thenReturn(release(9, "1.0.0", 0, 0));
        assertThrows(BusinessException.class, () -> service.postUpdate(9, 2, ""));
        service.postUpdate(9, 0, "U12345678901");
        verify(updates).updateById(argThat(value -> value.getStatus() == 0 && "".equals(value.getGrayscaleUid())), eq(9));
        when(updates.selectById(9)).thenReturn(release(9, "1.0.0", 0, 1));
        assertThrows(BusinessException.class, () -> service.postUpdate(9, 1, "U123"));
        service.postUpdate(9, 1, "U12345678901, U98765432109,U12345678901");
        verify(updates).updateById(argThat(value -> value.getStatus() == 1 && "U12345678901,U98765432109".equals(value.getGrayscaleUid())), eq(9));
    }
}
