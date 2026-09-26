package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.enums.AppUpdateFileTypeEnum;
import top.enderherman.wetalk.entity.enums.AppUpdateStatusEnum;
import top.enderherman.wetalk.entity.po.AppUpdate;
import top.enderherman.wetalk.entity.query.AppUpdateQuery;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.AppUpdateMapper;
import top.enderherman.wetalk.service.impl.AppUpdateServiceImpl;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppUpdateDownloadTest {
    @TempDir
    Path data;

    private AppUpdateServiceImpl service;
    private AppUpdateMapper<AppUpdate, AppUpdateQuery> updates;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        service = new AppUpdateServiceImpl();
        updates = mock(AppUpdateMapper.class);
        AppConfig config = new AppConfig();
        config.setProjectFolder(data.toString());
        ReflectionTestUtils.setField(service, "appUpdateMapper", updates);
        ReflectionTestUtils.setField(service, "appConfig", config);

        Path updateFolder = data.resolve(Constants.FILE_FOLDER).resolve(Constants.APP_UPDATE_FILE);
        Files.createDirectories(updateFolder);
        Files.writeString(updateFolder.resolve("9" + Constants.APP_EXE_SUFFIX), "published package");
    }

    private AppUpdate update(int id, int status, String grayscaleUid, int fileType) {
        AppUpdate update = new AppUpdate();
        update.setId(id);
        update.setStatus(status);
        update.setGrayscaleUid(grayscaleUid);
        update.setFileType(fileType);
        return update;
    }

    @Test
    void allReleasedUpdateIsDownloadableAndResolvesInsideStorage() throws Exception {
        when(updates.selectById(9)).thenReturn(update(9, AppUpdateStatusEnum.ALL.getStatus(), "", AppUpdateFileTypeEnum.LOCAL.getType()));

        Path file = service.getDownloadFile(9, "U100").toPath();

        assertEquals(data.resolve(Constants.FILE_FOLDER).resolve(Constants.APP_UPDATE_FILE)
                .resolve("9" + Constants.APP_EXE_SUFFIX).toAbsolutePath().normalize(), file);
        assertEquals("published package", Files.readString(file));
    }

    @Test
    void onlyListedUsersCanDownloadGrayscaleRelease() {
        when(updates.selectById(9)).thenReturn(update(9, AppUpdateStatusEnum.GRAYSCALE.getStatus(), "U100,U200", AppUpdateFileTypeEnum.LOCAL.getType()));

        assertEquals("9" + Constants.APP_EXE_SUFFIX, service.getDownloadFile(9, "U200").getName());
        assertThrows(BusinessException.class, () -> service.getDownloadFile(9, "U300"));
    }

    @Test
    void unpublishedUpdateIsNotDownloadable() {
        when(updates.selectById(9)).thenReturn(update(9, AppUpdateStatusEnum.INIT.getStatus(), "", AppUpdateFileTypeEnum.LOCAL.getType()));

        assertThrows(BusinessException.class, () -> service.getDownloadFile(9, "U100"));
    }

    @Test
    void externalLinkUpdateIsNotServedAsALocalFile() {
        when(updates.selectById(9)).thenReturn(update(9, AppUpdateStatusEnum.ALL.getStatus(), "", AppUpdateFileTypeEnum.OUTER_LINK.getType()));

        assertThrows(BusinessException.class, () -> service.getDownloadFile(9, "U100"));
    }

    @Test
    void missingPackageAndInvalidIdAreRejected() {
        when(updates.selectById(10)).thenReturn(update(10, AppUpdateStatusEnum.ALL.getStatus(), "", AppUpdateFileTypeEnum.LOCAL.getType()));

        assertThrows(BusinessException.class, () -> service.getDownloadFile(10, "U100"));
        assertThrows(BusinessException.class, () -> service.getDownloadFile(0, "U100"));
    }
}
