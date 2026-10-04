package top.enderherman.wetalk.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.enums.AppUpdateFileTypeEnum;
import top.enderherman.wetalk.entity.enums.AppUpdateStatusEnum;
import top.enderherman.wetalk.entity.enums.PageSize;
import top.enderherman.wetalk.entity.po.AppUpdate;
import top.enderherman.wetalk.entity.query.AppUpdateQuery;
import top.enderherman.wetalk.entity.query.SimplePage;
import top.enderherman.wetalk.entity.vo.AppUpdateVO;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.AppUpdateMapper;
import top.enderherman.wetalk.service.AppUpdateService;
import top.enderherman.wetalk.utils.CopyUtils;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.utils.AppVersion;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.net.URI;
import java.util.Locale;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


/**
 * app发布表 业务接口实现
 */
@Service("appUpdateService")
public class AppUpdateServiceImpl implements AppUpdateService {

    @Resource
    private AppUpdateMapper<AppUpdate, AppUpdateQuery> appUpdateMapper;

    @Resource
    private AppConfig appConfig;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<AppUpdate> findListByParam(AppUpdateQuery param) {
        return this.appUpdateMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(AppUpdateQuery param) {
        return this.appUpdateMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<AppUpdate> findListByPage(AppUpdateQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<AppUpdate> list = this.findListByParam(param);
        PaginationResultVO<AppUpdate> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(AppUpdate bean) {
        return this.appUpdateMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<AppUpdate> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.appUpdateMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<AppUpdate> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.appUpdateMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(AppUpdate bean, AppUpdateQuery param) {
        StringUtils.checkParam(param);
        return this.appUpdateMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(AppUpdateQuery param) {
        StringUtils.checkParam(param);
        return this.appUpdateMapper.deleteByParam(param);
    }

    /**
     * 根据Id获取对象
     */
    @Override
    public AppUpdate getAppUpdateById(Integer id) {
        return this.appUpdateMapper.selectById(id);
    }

    @Override
    public File getDownloadFile(Integer id, String userId) {
        if (id == null || id <= 0 || StringUtils.isEmpty(userId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        AppUpdate update = appUpdateMapper.selectById(id);
        if (update == null || !canUserDownload(update, userId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
        if (!AppUpdateFileTypeEnum.LOCAL.getType().equals(update.getFileType())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        Path updateFolder = Path.of(appConfig.getProjectFolder(), Constants.FILE_FOLDER, Constants.APP_UPDATE_FILE)
                .toAbsolutePath().normalize();
        Path updatePath = updateFolder.resolve(id + Constants.APP_EXE_SUFFIX).normalize();
        if (!updatePath.startsWith(updateFolder)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        File file = updatePath.toFile();
        if (!file.isFile()) {
            throw new BusinessException(ResponseCodeEnum.CODE_602);
        }
        return file;
    }

    private boolean canUserDownload(AppUpdate update, String userId) {
        if (AppUpdateStatusEnum.ALL.getStatus().equals(update.getStatus())) return true;
        if (!AppUpdateStatusEnum.GRAYSCALE.getStatus().equals(update.getStatus())
                || StringUtils.isEmpty(update.getGrayscaleUid())) return false;
        return Arrays.stream(update.getGrayscaleUid().split(",")).anyMatch(userId::equals);
    }

    /**
     * 根据Id修改
     */
    @Override
    public Integer updateAppUpdateById(AppUpdate bean, Integer id) {
        return this.appUpdateMapper.updateById(bean, id);
    }

    /**
     * 根据Id删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer deleteAppUpdateById(Integer id) {
        lockReleaseCatalog();
        AppUpdate dbInfo = requireUpdateForUpdate(id);
        if (!AppUpdateStatusEnum.INIT.getStatus().equals(dbInfo.getStatus())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        Integer removed = appUpdateMapper.deleteDraftById(id);
        if (removed == null || removed != 1) throw new BusinessException("版本状态已变化，请刷新后重试");
        return removed;
    }

    /**
     * 发布或者修改更新
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUpdate(AppUpdate appUpdate, MultipartFile file) throws IOException {
        appUpdate.setVersion(AppVersion.normalize(appUpdate.getVersion()));
        if (appUpdate.getUpdateDesc() == null || appUpdate.getUpdateDesc().isBlank()
                || appUpdate.getUpdateDesc().length() > 500) {
            throw new BusinessException("更新说明不能为空且不能超过 500 个字符");
        }
        AppUpdateFileTypeEnum fileTypeEnum = AppUpdateFileTypeEnum.getByType(appUpdate.getFileType());
        if (null == fileTypeEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        lockReleaseCatalog();
        AppUpdate existing = null;
        if (appUpdate.getId() != null) {
            existing = requireUpdateForUpdate(appUpdate.getId());
            if (!AppUpdateStatusEnum.INIT.getStatus().equals(existing.getStatus())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }

        for (AppUpdate other : appUpdateMapper.selectAllForUpdate()) {
            if (appUpdate.getId() != null && appUpdate.getId().equals(other.getId())) continue;
            if (AppVersion.isValid(other.getVersion()) && AppVersion.compare(appUpdate.getVersion(), other.getVersion()) <= 0) {
                throw new BusinessException("当前版本必须大于历史版本");
            }
        }
        if (fileTypeEnum == AppUpdateFileTypeEnum.OUTER_LINK) {
            validateOuterLink(appUpdate.getOuterLink());
            if (file != null) throw new BusinessException("外链版本不能同时上传安装包");
        } else {
            appUpdate.setOuterLink("");
            if (file == null && (existing == null || !AppUpdateFileTypeEnum.LOCAL.getType().equals(existing.getFileType())
                    || !Files.isRegularFile(updatePath(existing.getId())))) {
                throw new BusinessException("请上传 Windows 安装包");
            }
            if (file != null && (file.isEmpty() || file.getSize() > 500L * 1024 * 1024
                    || file.getOriginalFilename() == null
                    || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".exe"))) {
                throw new BusinessException("安装包须为非空 .exe 文件，最大 500 MiB");
            }
        }
        // Stage uploads before changing the record; a failed transfer keeps any previous package intact.
        Path staged = null;
        if (file != null) {
            Files.createDirectories(updateFolder());
            staged = Files.createTempFile(updateFolder(), "upload-", ".tmp");
        }
        try {
        if (file != null) file.transferTo(staged);
        //更新数据库
        if (appUpdate.getId() == null) {
            appUpdate.setCreateTime(new Date());
            appUpdate.setStatus(AppUpdateStatusEnum.INIT.getStatus());
            appUpdateMapper.insert(appUpdate);
        } else {
            appUpdate.setStatus(null);
            appUpdate.setGrayscaleUid(null);
            boolean unchanged = java.util.Objects.equals(existing.getVersion(), appUpdate.getVersion())
                    && java.util.Objects.equals(existing.getUpdateDesc(), appUpdate.getUpdateDesc())
                    && java.util.Objects.equals(existing.getFileType(), appUpdate.getFileType())
                    && java.util.Objects.equals(existing.getOuterLink(), appUpdate.getOuterLink());
            if (!unchanged) {
                Integer changed = appUpdateMapper.updateDraftById(appUpdate, appUpdate.getId());
                if (changed == null || changed != 1) throw new BusinessException("版本状态已变化，请刷新后重试");
            }
        }

        if (staged != null) Files.move(staged, updatePath(appUpdate.getId()), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (staged != null) Files.deleteIfExists(staged);
        }
    }

    /**
     * 发布更新
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postUpdate(Integer id, Integer status, String grayscaleUid) {
        lockReleaseCatalog();
        AppUpdate existing = requireUpdateForUpdate(id);
        AppUpdateStatusEnum statusEnum = AppUpdateStatusEnum.getByStatus(status);
        if (null == statusEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (AppUpdateStatusEnum.GRAYSCALE.equals(statusEnum)) {
            if (StringUtils.isEmpty(grayscaleUid)) throw new BusinessException("请填写灰度用户编号");
            grayscaleUid = Arrays.stream(grayscaleUid.split("[,，\\s]+"))
                    .filter(value -> !value.isBlank()).distinct().collect(java.util.stream.Collectors.joining(","));
            if (grayscaleUid.isEmpty() || grayscaleUid.length() > 1000
                    || Arrays.stream(grayscaleUid.split(",")).anyMatch(value -> !value.matches("U[0-9]{11}"))) {
                throw new BusinessException("灰度用户编号格式不正确");
            }
        }
        if (!AppUpdateStatusEnum.GRAYSCALE.equals(statusEnum)) {
            grayscaleUid = "";
        }
        if (statusEnum != AppUpdateStatusEnum.INIT) {
            AppVersion.validate(existing.getVersion());
            if (AppUpdateFileTypeEnum.LOCAL.getType().equals(existing.getFileType())) {
                File packageFile = updatePath(id).toFile();
                if (!packageFile.isFile() || packageFile.length() == 0) throw new BusinessException("安装包不存在或为空，无法发布");
            } else if (AppUpdateFileTypeEnum.OUTER_LINK.getType().equals(existing.getFileType())) {
                validateOuterLink(existing.getOuterLink());
            } else throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (status.equals(existing.getStatus())
                && grayscaleUid.equals(existing.getGrayscaleUid() == null ? "" : existing.getGrayscaleUid())) return;
        Integer changed = appUpdateMapper.updatePublicationState(id, existing.getStatus(), status, grayscaleUid);
        if (changed == null || changed != 1) throw new BusinessException("版本状态已变化，请刷新后重试");
    }

    /**
     * 获取最后更新版本
     */
    @Override
    public AppUpdateVO getLatestUpdate(String version, String uid) {
        AppVersion.validate(version);
        AppUpdate update = appUpdateMapper.selectVisibleUpdates(uid).stream()
                .filter(item -> canUserDownload(item, uid) && AppVersion.isValid(item.getVersion()))
                .filter(item -> AppVersion.compare(item.getVersion(), version) > 0)
                .max((first, second) -> AppVersion.compare(first.getVersion(), second.getVersion()))
                .orElse(null);
        if (update == null)
            return null;
        AppUpdateVO vo = CopyUtils.copy(update, AppUpdateVO.class);
        if (AppUpdateFileTypeEnum.LOCAL.getType().equals(update.getFileType())) {
            File file = updatePath(update.getId()).toFile();
            vo.setSize(file.length());
        } else {
            vo.setSize(0L);
        }
        vo.setUpdateList(update.getUpdateDescArray() == null ? List.of() : Arrays.asList(update.getUpdateDescArray()));
        String fileName = Constants.APP_NAME + update.getVersion() + Constants.APP_EXE_SUFFIX;
        vo.setFileName(fileName);
        return vo;
    }

    private void lockReleaseCatalog() {
        if (!Integer.valueOf(1).equals(appUpdateMapper.lockReleaseCatalog())) {
            throw new BusinessException("版本发布数据尚未初始化");
        }
    }

    private AppUpdate requireUpdateForUpdate(Integer id) {
        if (id == null || id <= 0) throw new BusinessException(ResponseCodeEnum.CODE_600);
        AppUpdate update = appUpdateMapper.selectByIdForUpdate(id);
        if (update == null) throw new BusinessException(ResponseCodeEnum.CODE_404);
        return update;
    }

    private Path updateFolder() {
        return Path.of(appConfig.getProjectFolder(), Constants.FILE_FOLDER, Constants.APP_UPDATE_FILE).toAbsolutePath().normalize();
    }

    private Path updatePath(Integer id) {
        return updateFolder().resolve(id + Constants.APP_EXE_SUFFIX);
    }

    private void validateOuterLink(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value);
            if (value.length() > 200 || uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new BusinessException("请输入有效的 HTTP 或 HTTPS 外链，最多 200 个字符");
        }
    }


}
