package top.enderherman.wetalk.service.impl;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.enums.*;
import top.enderherman.wetalk.entity.po.*;
import top.enderherman.wetalk.entity.query.*;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.*;
import top.enderherman.wetalk.service.GroupInfoService;
import top.enderherman.wetalk.service.UserContactService;
import top.enderherman.wetalk.utils.CopyUtils;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;


/**
 * 群组信息表 业务接口实现
 */
@Service("groupInfoService")
public class GroupInfoServiceImpl implements GroupInfoService {

    @Lazy
    @Resource
    private GroupInfoService groupInfoService;

    @Resource
    private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Resource
    private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

    @Resource
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

    @Resource
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Resource
    private ChatSessionUserServiceImpl chatSessionUserService;

    @Resource
    private AppConfig appConfig;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private UserContactService userContactService;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;


    /**
     * 根据条件查询列表
     */
    @Override
    public List<GroupInfo> findListByParam(GroupInfoQuery param) {
        return this.groupInfoMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(GroupInfoQuery param) {
        return this.groupInfoMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<GroupInfo> findListByPage(GroupInfoQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<GroupInfo> list = this.findListByParam(param);
        return new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
    }

    /**
     * 新增
     */
    @Override
    public Integer add(GroupInfo bean) {
        return this.groupInfoMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<GroupInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.groupInfoMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<GroupInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.groupInfoMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(GroupInfo bean, GroupInfoQuery param) {
        StringUtils.checkParam(param);
        return this.groupInfoMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(GroupInfoQuery param) {
        StringUtils.checkParam(param);
        return this.groupInfoMapper.deleteByParam(param);
    }

    /**
     * 根据GroupId获取对象
     */
    @Override
    public GroupInfo getGroupInfoByGroupId(String groupId) {
        return this.groupInfoMapper.selectByGroupId(groupId);
    }

    /**
     * 根据GroupId修改
     */
    @Override
    public Integer updateGroupInfoByGroupId(GroupInfo bean, String groupId) {
        return this.groupInfoMapper.updateByGroupId(bean, groupId);
    }

    /**
     * 根据GroupId删除
     */
    @Override
    public Integer deleteGroupInfoByGroupId(String groupId) {
        return this.groupInfoMapper.deleteByGroupId(groupId);
    }

    /**
     * 新增或修改群聊
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveGroup(GroupInfo groupInfo, MultipartFile avatarFile, MultipartFile avatarCover) {
        Date currentDate = new Date();
        //1.新增
        if (StringUtils.isEmpty(groupInfo.getGroupId())) {

            //1.1查询已有群组数量
            GroupInfoQuery query = new GroupInfoQuery();
            query.setGroupOwnId(groupInfo.getGroupOwnId());
            Integer count = groupInfoMapper.selectCount(query);
            groupInfo.setGroupId(StringUtils.getGroupId());
            SysSettingDto sysSettingDto = redisComponent.getSysSetting();
            if (count >= sysSettingDto.getMaxGroupCount()) {
                throw new BusinessException("最多支持创建" + sysSettingDto.getMaxGroupCount() + "个群聊");
            }

            if (avatarFile == null) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }

            groupInfo.setCreateTime(currentDate);
            groupInfo.setGroupId(StringUtils.getGroupId());
            groupInfoMapper.insert(groupInfo);

            //1.2将群组设置为联系人
            UserContact userContact = new UserContact();
            userContact.setUserId(groupInfo.getGroupOwnId());
            userContact.setContactId(groupInfo.getGroupId());
            userContact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            userContact.setContactType(UserContactTypeEnum.GROUP.getType());
            userContact.setCreateTime(currentDate);
            userContactMapper.insert(userContact);

            // 2.1创建会话
            String sessionId = StringUtils.getChatSessionId4Group(groupInfo.getGroupId());
            ChatSession chatSession = new ChatSession();
            chatSession.setSessionId(sessionId);
            chatSession.setLastMessage(MessageTypeEnum.GROUP_CREATE.getInitMessage());
            chatSession.setLastReceiveTime(currentDate.getTime());
            chatSessionMapper.insert(chatSession);

            // 2.2创建群组与会话之间的关系
            ChatSessionUser chatSessionUser = new ChatSessionUser();
            chatSessionUser.setUserId(groupInfo.getGroupOwnId());
            chatSessionUser.setContactId(groupInfo.getGroupId());
            chatSessionUser.setSessionId(sessionId);
            chatSessionUser.setContactName(groupInfo.getGroupName());
            chatSessionUserMapper.insert(chatSessionUser);


            // 2.3发送欢迎消息
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSessionId(sessionId);
            chatMessage.setMessageType(MessageTypeEnum.GROUP_CREATE.getType());
            chatMessage.setMessageContent(MessageTypeEnum.GROUP_CREATE.getInitMessage());
            chatMessage.setSendTime(currentDate.getTime());
            chatMessage.setContactId(groupInfo.getGroupId());
            chatMessage.setContactType(UserContactTypeEnum.GROUP.getType());
            chatMessage.setStatus(MessageStatusEnum.SENT.getStatus());
            chatMessageMapper.insert(chatMessage);
            //2.4 群组添加到联系人联系人缓存中
            redisComponent.saveContact(groupInfo.getGroupOwnId(), groupInfo.getGroupId());
            //2.5 将联系人通道添加到群组通道
            channelContextUtils.addUser2Group(groupInfo.getGroupOwnId(), groupInfo.getGroupId());
            //2.6 发送ws消息
            chatSessionUser.setLastMessage(MessageTypeEnum.GROUP_CREATE.getInitMessage());
            chatSessionUser.setLastReceiveTime(currentDate.getTime());
            chatSessionUser.setMemberCount(1);
            MessageSendDTO messageSendDTO = CopyUtils.copy(chatMessage, MessageSendDTO.class);
            messageSendDTO.setExtentData(chatSessionUser);
            messageSendDTO.setLastMessage(MessageTypeEnum.GROUP_CREATE.getInitMessage());
            messageHandler.sendMessage(messageSendDTO);


        }
        //2.修改
        else {
            GroupInfo dbInfo = groupInfoMapper.selectByGroupId(groupInfo.getGroupId());
            if (!dbInfo.getGroupOwnId().equals(groupInfo.getGroupOwnId())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }

            groupInfoMapper.updateByGroupId(groupInfo, groupInfo.getGroupId());
            //更新相关表冗余信息
            String contactName = null;
            if (!dbInfo.getGroupName().equals(groupInfo.getGroupName())) {
                contactName = groupInfo.getGroupName();
            }
            if (contactName != null) {
                //更新聊天中名称
                chatSessionUserService.updateRedundancyInfo(contactName, groupInfo.getGroupId());
            }


        }

        //3.群头像处理
        if (avatarFile == null) {
            return;
        }
        //3.1头像目录
        String baseFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER;
        File avatarFolder = new File(baseFolder + Constants.AVATAR_FOLDER);
        if (!avatarFolder.exists()) {
            avatarFolder.mkdirs();
        }
        //3.2头像路径
        String filePath = avatarFolder.getPath() + "/" + groupInfo.getGroupId() + Constants.IMAGE_SUFFIX;
        try {
            avatarFile.transferTo(new File(filePath));
            if (avatarCover != null) {
                avatarCover.transferTo(new File(filePath + Constants.COVER_IMAGE_SUFFIX));
            }
        } catch (IOException e) {
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }


    }

    /**
     * 退出或被移除群聊
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveGroup(String userId, String groupId, MessageTypeEnum messageTypeEnum) {
        GroupInfo groupInfo = groupInfoMapper.selectByGroupId(groupId);
        if (groupInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //创建者不能退群 只能解散
        if (userId.equals(groupInfo.getGroupOwnId())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        Integer count = userContactMapper.deleteByUserIdAndContactId(userId, groupId);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        chatSessionUserMapper.deleteByUserIdAndContactId(userId, groupId);
        UserInfo user = userInfoMapper.selectByUserId(userId);
        String sessionId = StringUtils.getChatSessionId4Group(groupId);
        Date curTime = new Date();
        String messageContent = String.format(messageTypeEnum.getInitMessage(), user.getNickName());

        // 1.更新会话消息
        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId(sessionId);
        chatSession.setLastMessage(messageContent);
        chatSession.setLastReceiveTime(curTime.getTime());
        chatSessionMapper.updateBySessionId(chatSession, sessionId);
        // 2.记录群消息
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(sessionId);
        chatMessage.setSendTime(curTime.getTime());
        chatMessage.setContactType(UserContactTypeEnum.GROUP.getType());
        chatMessage.setStatus(MessageStatusEnum.SENT.getStatus());
        chatMessage.setMessageType(messageTypeEnum.getType());
        chatMessage.setContactId(groupId);
        chatMessage.setMessageContent(messageContent);
        chatMessageMapper.insert(chatMessage);
        // 3.发送解散通知消息
        UserContactQuery userContactQuery = new UserContactQuery();
        userContactQuery.setContactId(groupId);
        userContactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
        Integer memberCount = userContactMapper.selectCount(userContactQuery);

        MessageSendDTO messageSendDTO = CopyUtils.copy(chatMessage, MessageSendDTO.class);
        messageSendDTO.setExtentData(userId);
        messageSendDTO.setMemberCount(memberCount);
        messageHandler.sendMessage(messageSendDTO);

    }

    /**
     * 解散群聊
     */
    @Override
    public void dissolutionGroup(String groupOwnerId, String groupId) {
        GroupInfo dbInfo = groupInfoMapper.selectByGroupId(groupId);
        if (dbInfo == null || !dbInfo.getGroupOwnId().equals(groupOwnerId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        //删除群组
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setStatus(GroupStatusEnum.DISSOLUTION.getStatus());
        groupInfoMapper.updateByGroupId(groupInfo, groupId);

        //更新联系人信息
        UserContactQuery userContactQuery = new UserContactQuery();
        userContactQuery.setContactId(groupId);
        userContactQuery.setContactType(UserContactTypeEnum.GROUP.getType());

        UserContact userContact = new UserContact();
        userContact.setStatus(UserContactStatusEnum.DEL.getStatus());
        userContactMapper.updateByParam(userContact, userContactQuery);

        ChatSessionUserQuery chatSessionUserQuery = new ChatSessionUserQuery();
        chatSessionUserQuery.setContactId(groupId);
        chatSessionUserMapper.deleteByParam(chatSessionUserQuery);

        //移除相关联系人缓存
        List<UserContact> userContactList = userContactMapper.selectList(userContactQuery);
        for (UserContact userContact2 : userContactList) {
            redisComponent.removeUserContact(userContact2.getUserId(), userContact2.getContactId());
        }

        String sessionId = StringUtils.getChatSessionId4Group(groupId);
        Date currentDate = new Date();
        String messageContent = MessageTypeEnum.DISSOLUTION_GROUP.getInitMessage();
        ChatSession chatSession = new ChatSession();
        chatSession.setLastMessage(messageContent);
        chatSession.setLastReceiveTime(currentDate.getTime());
        chatSessionMapper.updateBySessionId(chatSession, sessionId);


        //更新会话消息 记录群消息
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(sessionId);
        chatMessage.setSendTime(currentDate.getTime());
        chatMessage.setContactType(UserContactTypeEnum.GROUP.getType());
        chatMessage.setStatus(MessageStatusEnum.SENT.getStatus());
        chatMessage.setMessageType(MessageTypeEnum.DISSOLUTION_GROUP.getType());
        chatMessage.setContactId(groupId);
        chatMessage.setMessageContent(messageContent);
        chatMessageMapper.insert(chatMessage);

        //发送群解散消息
        MessageSendDTO messageSendDTO = CopyUtils.copy(chatMessage, MessageSendDTO.class);
        messageHandler.sendMessage(messageSendDTO);
    }

    /**
     * 增加或者移除群成员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrRemoveGroupUser(TokenUserInfoDto tokenUserInfoDto, String groupId, String contactIds, Integer opType) {
        GroupInfo dbInfo = groupInfoMapper.selectByGroupId(groupId);
        if (dbInfo == null || !dbInfo.getGroupOwnId().equals(tokenUserInfoDto.getUserId())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String[] contactIdList = contactIds.split(",");
        for (String contactId : contactIdList) {
            // 移除群员
            if (Constants.ZERO.equals(opType)) {
                //启用事务 交给spring管理
                groupInfoService.leaveGroup(contactId, groupId, MessageTypeEnum.REMOVE_GROUP);
            } else {
                userContactService.addContact(contactId, null, groupId, UserContactTypeEnum.GROUP.getType(), "用户" + contactId + "加入群聊");
            }
        }
    }
}
