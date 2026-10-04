package top.enderherman.wetalk.controller;


import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.annotation.GlobalInterceptor;
import top.enderherman.wetalk.common.BaseResponse;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.po.GroupInfo;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.query.GroupInfoQuery;
import top.enderherman.wetalk.entity.query.UserInfoQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.service.GroupInfoService;
import top.enderherman.wetalk.service.UserInfoService;
import top.enderherman.wetalk.utils.ImageUploadValidator;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;

@Validated
@RestController
@RequestMapping("/admin")
public class ManageController extends ABaseController {

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private GroupInfoService groupInfoService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private AppConfig appConfig;

    /**
     * 获取用户信息
     */
    @RequestMapping("/loadUser")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> loadUser(UserInfoQuery userInfoQuery) {
        userInfoQuery.setOrderBy("create_time desc");
        PaginationResultVO<UserInfo> list = userInfoService.findListByPage(userInfoQuery);
        return getSuccessResponseVO(list);
    }

    /**
     * 更改用户账户状态
     */
    @RequestMapping("/updateUserStatus")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> updateUserStatus(HttpServletRequest request, @NotNull String userId, @NotNull Integer status) {
        requireOtherUser(request, userId);
        userInfoService.updateUserStatus(userId, status);
        return getSuccessResponseVO(null);
    }

    /**
     * 强制下线
     */
    @RequestMapping("/forcedOffOnline")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> forcedOffOnline(HttpServletRequest request, @NotNull String userId) {
        requireOtherUser(request, userId);
        userInfoService.forcedOffOnline(userId);
        return getSuccessResponseVO(null);
    }

    /**
     * 获取所有群组信息
     */
    @RequestMapping("/loadGroup")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<PaginationResultVO<GroupInfo>> loadGroup(GroupInfoQuery query) {
        query.setOrderBy("create_time desc");
        query.setQueryGroupOwnerName(true);
        query.setQueryMemberCount(true);
        PaginationResultVO<GroupInfo> listByPage = groupInfoService.findListByPage(query);
        return BaseResponse.success(listByPage);
    }

    /**
     * 直接解散群组
     */
    @RequestMapping("/dissolutionGroup")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> dissolutionGroup(@NotNull String groupOwnerId, @NotNull String groupId) {
        groupInfoService.dissolutionGroup(groupOwnerId, groupId);
        return BaseResponse.success();
    }

    /**
     * 获取系统设置
     */
    @RequestMapping("/getSystemSetting")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> getSystemSetting() {
        SysSettingDto sysSettingDto = redisComponent.getSysSetting();
        return BaseResponse.success(sysSettingDto);
    }


    /**
     * 保存系统设置
     */
    @RequestMapping("/saveSystemSetting")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> saveSystemSetting(SysSettingDto sysSettingDto,
                                             MultipartFile robotAvatarFile,
                                             MultipartFile robotAvatarCoverFile) throws IOException {
        validateSettings(sysSettingDto);
        ImageUploadValidator.validate(robotAvatarFile);
        ImageUploadValidator.validate(robotAvatarCoverFile);
        sysSettingDto.setRobotUid(Constants.ROBOT_UID);
        if (robotAvatarFile != null || robotAvatarCoverFile != null) {
            String baseFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER;
            File targetFileFolder = new File(baseFolder + Constants.AVATAR_FOLDER);
            if (!targetFileFolder.exists()) {
                targetFileFolder.mkdirs();
            }
            String filePath = targetFileFolder.getPath() + "/" + Constants.ROBOT_UID + Constants.IMAGE_SUFFIX;
            if (robotAvatarFile != null) robotAvatarFile.transferTo(new File(filePath));
            if (robotAvatarCoverFile != null) robotAvatarCoverFile.transferTo(new File(filePath + Constants.COVER_IMAGE_SUFFIX));
        }
        redisComponent.saveSysSetting(sysSettingDto);
        return BaseResponse.success();
    }

    private void requireOtherUser(HttpServletRequest request, String userId) {
        if (getTokenUserDto(request).getUserId().equals(userId)) {
            throw new BusinessException("不能操作当前管理员账号");
        }
    }

    private void validateSettings(SysSettingDto settings) {
        if (settings == null) throw new BusinessException(ResponseCodeEnum.CODE_600);
        Integer[] quotas = {settings.getMaxGroupCount(), settings.getMaxGroupMemberCount(),
                settings.getMaxImageSize(), settings.getMaxVideoSize(), settings.getMaxFileSize()};
        for (Integer quota : quotas) {
            if (quota == null || quota < 1) throw new BusinessException("系统配额必须是大于 0 的整数");
        }
        if (settings.getRobotNickName() == null || settings.getRobotNickName().isBlank()
                || settings.getRobotNickName().trim().length() > 20
                || settings.getRobotWelcome() == null || settings.getRobotWelcome().isBlank()
                || settings.getRobotWelcome().length() > 300) {
            throw new BusinessException("机器人昵称须为 1 至 20 字符，欢迎语须为 1 至 300 字符");
        }
        settings.setRobotNickName(settings.getRobotNickName().trim());
    }


}
