package top.enderherman.wetalk.service.impl;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.enums.*;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.po.UserInfoBeauty;
import top.enderherman.wetalk.entity.query.SimplePage;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.entity.query.UserInfoBeautyQuery;
import top.enderherman.wetalk.entity.query.UserInfoQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.entity.vo.UserInfoVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.UserContactMapper;
import top.enderherman.wetalk.mappers.UserInfoBeautyMapper;
import top.enderherman.wetalk.mappers.UserInfoMapper;
import top.enderherman.wetalk.service.ChatSessionUserService;
import top.enderherman.wetalk.service.UserContactService;
import top.enderherman.wetalk.service.UserInfoService;
import top.enderherman.wetalk.utils.CopyUtils;
import top.enderherman.wetalk.utils.ImageUploadValidator;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 用户表 业务接口实现
 */
@Service("userInfoService")
public class UserInfoServiceImpl implements UserInfoService {

    @Resource
    private AppConfig appConfig;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @Resource
    private UserContactService userContactService;

    @Resource
    private ChatSessionUserService chatSessionUserService;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private UserInfoBeautyMapper<UserInfoBeauty, UserInfoBeautyQuery> userInfoBeautyMapper;

    @Resource
    private UserContactMapper<UserContact,UserContactQuery> userContactMapper;



    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserInfo> findListByParam(UserInfoQuery param) {
        return this.userInfoMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserInfoQuery param) {
        return this.userInfoMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserInfo> list = this.findListByParam(param);
        PaginationResultVO<UserInfo> result = new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(UserInfo bean) {
        return this.userInfoMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<UserInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userInfoMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<UserInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userInfoMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(UserInfo bean, UserInfoQuery param) {
        StringUtils.checkParam(param);
        return this.userInfoMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(UserInfoQuery param) {
        StringUtils.checkParam(param);
        return this.userInfoMapper.deleteByParam(param);
    }

    /**
     * 根据UserId获取对象
     */
    @Override
    public UserInfo getUserInfoByUserId(String userId) {
        return this.userInfoMapper.selectByUserId(userId);
    }

    /**
     * 根据UserId修改
     */
    @Override
    public Integer updateUserInfoByUserId(UserInfo bean, String userId) {
        return this.userInfoMapper.updateByUserId(bean, userId);
    }

    /**
     * 根据UserId删除
     */
    @Override
    public Integer deleteUserInfoByUserId(String userId) {
        return this.userInfoMapper.deleteByUserId(userId);
    }

    /**
     * 根据Email获取对象
     */
    @Override
    public UserInfo getUserInfoByEmail(String email) {
        return this.userInfoMapper.selectByEmail(email);
    }

    /**
     * 根据Email修改
     */
    @Override
    public Integer updateUserInfoByEmail(UserInfo bean, String email) {
        return this.userInfoMapper.updateByEmail(bean, email);
    }

    /**
     * 根据Email删除
     */
    @Override
    public Integer deleteUserInfoByEmail(String email) {
        return this.userInfoMapper.deleteByEmail(email);
    }


    /**
     * 注册
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String nickName, String password) {
        if (isAdminEmail(email)) {
            throw new BusinessException("Administrator accounts must be provisioned separately");
        }
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo != null) {
            throw new BusinessException("邮箱已存在");
        }

        String userId = StringUtils.getUserId();
        UserInfoBeauty beautyAccount = userInfoBeautyMapper.selectByEmail(email);
        //靓号功能 首先不为空 且未使用
        Boolean useBeautyAccount = beautyAccount != null && BeautyAccountStatusEnum.NO_USE.getStatus().equals(beautyAccount.getStatus());
        if (useBeautyAccount) {
            userId = UserContactTypeEnum.USER.getPrefix() + beautyAccount.getUserId();
            //将靓号设置为已使用
            UserInfoBeauty updateUserInfoBeauty = new UserInfoBeauty();
            updateUserInfoBeauty.setStatus(BeautyAccountStatusEnum.USEED.getStatus());
            userInfoBeautyMapper.updateByUserId(updateUserInfoBeauty, beautyAccount.getUserId());
        }

        Date current = new Date();
        userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setNickName(nickName);
        userInfo.setEmail(email);
        userInfo.setPassword(StringUtils.encodingByMd5(password));
        userInfo.setCreateTime(current);
        userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
        userInfo.setLastOffTime(current.getTime()-1000);
        userInfo.setJoinType(JoinTypeEnum.APPLY.getType());
        userInfoMapper.insert(userInfo);

        //添加机器人好友
        userContactService.addRobotContact(userInfo.getUserId());

    }

    /**
     * 登录
     */
    @Override
    public UserInfoVO login(String email, String password) {
        return login(email, password, SessionDeviceType.DESKTOP, "WeTalkApp");
    }

    @Override
    public UserInfoVO login(String email, String password, SessionDeviceType deviceType, String deviceName) {
        UserInfo userInfo = userInfoMapper.selectByEmail(email);

        if (userInfo == null || !userInfo.getPassword().equals(password)) {
            throw new BusinessException("账号或密码错误");
        }

        if (UserStatusEnum.DISABLE.getStatus().equals(userInfo.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }

        UserContactQuery userContactQuery = new UserContactQuery();
        userContactQuery.setUserId(userInfo.getUserId());
        userContactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
        List<UserContact> list = userContactMapper.selectList(userContactQuery);
        List<String> contactIdList=list.stream().map(UserContact::getContactId).collect(Collectors.toList());
        //保存联系人信息到redis中
        redisComponent.deleteContactBatch(userInfo.getUserId());
        if(!contactIdList.isEmpty()){
            redisComponent.saveContactBatch(userInfo.getUserId(), contactIdList);
        }

        TokenUserInfoDto dto = getTokenUserInfoDto(userInfo);
        SessionDeviceType resolvedDeviceType = deviceType == null ? SessionDeviceType.DESKTOP : deviceType;
        dto.setDeviceType(resolvedDeviceType.getValue());
        dto.setDeviceName(deviceName == null || deviceName.isBlank() ? "未知设备" : deviceName);
        List<TokenUserInfoDto> replacedSessions = redisComponent.replaceSameTypeLoginSession(dto);
        if (channelContextUtils != null && replacedSessions != null) {
            for (TokenUserInfoDto replaced : replacedSessions) {
                channelContextUtils.closeSession(userInfo.getUserId(), replaced.getSessionId());
            }
        }

        UserInfoVO userInfoVO = CopyUtils.copy(userInfo, UserInfoVO.class);
        userInfoVO.setToken(dto.getToken());
        userInfoVO.setAdmin(dto.isAdmin());
        return userInfoVO;
    }

    /**
     * 更新用户信息
     */
    @Override
    public void updateUserInfo(UserInfo userInfo, MultipartFile avatarFile, MultipartFile avatarCoverFile) throws IOException {
        // 仅复制可编辑资料字段；内部状态字段不能通过表单批量绑定修改。
        UserInfo update = new UserInfo();
        update.setUserId(userInfo.getUserId());
        update.setNickName(userInfo.getNickName());
        update.setJoinType(userInfo.getJoinType());
        update.setSex(userInfo.getSex());
        update.setPersonalSignature(userInfo.getPersonalSignature());
        update.setAreaName(userInfo.getAreaName());
        update.setAreaCode(userInfo.getAreaCode());
        userInfo = update;
        if ((userInfo.getNickName() != null && (userInfo.getNickName().isBlank() || userInfo.getNickName().length() > 40))
                || (userInfo.getJoinType() != null && JoinTypeEnum.getByType(userInfo.getJoinType()) == null)
                || (userInfo.getSex() != null && userInfo.getSex() != 0 && userInfo.getSex() != 1)
                || (userInfo.getPersonalSignature() != null && userInfo.getPersonalSignature().length() > 64)
                || (userInfo.getAreaName() != null && userInfo.getAreaName().length() > 64)
                || (userInfo.getAreaCode() != null && userInfo.getAreaCode().length() > 64)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserInfo dbInfo = userInfoMapper.selectByUserId(userInfo.getUserId());
        if (dbInfo == null) throw new BusinessException(ResponseCodeEnum.CODE_404);
        ImageUploadValidator.validate(avatarFile);
        ImageUploadValidator.validate(avatarCoverFile);
        if (avatarFile != null || avatarCoverFile != null) {
            String baseFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER;
            File targetFileFolder = new File(baseFolder + Constants.AVATAR_FOLDER);
            if (!targetFileFolder.exists()) {
                targetFileFolder.mkdirs();
            }
            String filePath = targetFileFolder.getPath() + "/" + userInfo.getUserId() + Constants.IMAGE_SUFFIX;
            if (avatarFile != null) avatarFile.transferTo(new File(filePath));
            if (avatarCoverFile != null) {
                avatarCoverFile.transferTo(new File(filePath + Constants.COVER_IMAGE_SUFFIX));
            }
        }

        userInfoMapper.updateByUserId(userInfo, userInfo.getUserId());
        //更新会话昵称
        String contactNameUpdate = null;
        if (userInfo.getNickName() != null && !userInfo.getNickName().equals(dbInfo.getNickName())) {
            contactNameUpdate = userInfo.getNickName();
        }
        if(contactNameUpdate == null){
            return;
        }

        //更新redis信息
        redisComponent.updateUserSessionsNickName(userInfo.getUserId(), contactNameUpdate);

        chatSessionUserService.updateRedundancyInfo(contactNameUpdate, userInfo.getUserId());

    }

    /**
     * 更新用户状态
     */
    @Override
    public void updateUserStatus(String userId, Integer status) {
        UserStatusEnum statusEnum = UserStatusEnum.getByStatus(status);
        if (statusEnum == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setStatus(status);
        userInfoMapper.updateByUserId(userInfo, userId);
        if (UserStatusEnum.DISABLE.getStatus().equals(status)) {
            forcedOffOnline(userId);
        }
    }

    /**
     * 强制用户下线
     */
    @Override
    public void forcedOffOnline(String userId) {
        redisComponent.clearTokenUserInfoDto(userId);
        MessageSendDTO<?> messageSendDTO = new MessageSendDTO<>();
        messageSendDTO.setContactType(UserContactTypeEnum.USER.getType());
        messageSendDTO.setMessageType(MessageTypeEnum.FORCE_OFF_LINE.getType());
        messageSendDTO.setContactId(userId);
        messageHandler.sendMessage(messageSendDTO);

    }

    private boolean isAdminEmail(String email) {
        return email != null && appConfig.getAdminEmails() != null
                && java.util.Arrays.stream(appConfig.getAdminEmails().split(","))
                .anyMatch(value -> value.trim().equalsIgnoreCase(email.trim()));
    }

    private TokenUserInfoDto getTokenUserInfoDto(UserInfo userInfo) {
        return TokenUserInfoDto.builder()
                .token(StringUtils.encodingByMd5(userInfo.getUserId()) + StringUtils.getRandomString(Constants.LENGTH_20))
                .userId(userInfo.getUserId())
                .nickName(userInfo.getNickName())
                .admin(isAdminEmail(userInfo.getEmail()))
                .build();
    }
}
