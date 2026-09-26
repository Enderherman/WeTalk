package top.enderherman.wetalk.service.impl;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.enums.*;
import top.enderherman.wetalk.entity.po.*;
import top.enderherman.wetalk.entity.query.*;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.entity.vo.UserContactSearchResultVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.*;
import top.enderherman.wetalk.service.UserContactService;
import top.enderherman.wetalk.utils.CopyUtils;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * 联系人表 业务接口实现
 */
@Service("userContactService")
public class UserContactServiceImpl implements UserContactService {

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;

    @Resource
    private UserContactApplyMapper<UserContactApply, UserContactApplyQuery> userContactApplyMapper;

    @Resource
    private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

    @Resource
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

    @Resource
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @Resource
    private MessageHandler messageHandler;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserContact> findListByParam(UserContactQuery param) {
        return this.userContactMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserContactQuery param) {
        return this.userContactMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserContact> findListByPage(UserContactQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserContact> list = this.findListByParam(param);
        PaginationResultVO<UserContact> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(UserContact bean) {
        return this.userContactMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<UserContact> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userContactMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<UserContact> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userContactMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(UserContact bean, UserContactQuery param) {
        StringUtils.checkParam(param);
        return this.userContactMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(UserContactQuery param) {
        StringUtils.checkParam(param);
        return this.userContactMapper.deleteByParam(param);
    }

    /**
     * 根据UserIdAndContactId获取对象
     */
    @Override
    public UserContact getUserContactByUserIdAndContactId(String userId, String contactId) {
        return this.userContactMapper.selectByUserIdAndContactId(userId, contactId);
    }

    /**
     * 根据UserIdAndContactId修改
     */
    @Override
    public Integer updateUserContactByUserIdAndContactId(UserContact bean, String userId, String contactId) {
        return this.userContactMapper.updateByUserIdAndContactId(bean, userId, contactId);
    }

    /**
     * 根据UserIdAndContactId删除
     */
    @Override
    public Integer deleteUserContactByUserIdAndContactId(String userId, String contactId) {
        return this.userContactMapper.deleteByUserIdAndContactId(userId, contactId);
    }

    /**
     * 搜索联系人
     */
    @Override
    public UserContactSearchResultVO searchContact(String userId, String contactId) {
        UserContactTypeEnum typeEnum = UserContactTypeEnum.getByPrefix(contactId);
        if (typeEnum == null) {
            return null;
        }
        UserContactSearchResultVO resultVO = new UserContactSearchResultVO();
        switch (typeEnum) {
            case USER:
                UserInfo userInfo = userInfoMapper.selectByUserId(contactId);
                if (userInfo == null) {
                    return null;
                }
                resultVO = CopyUtils.copy(userInfo, UserContactSearchResultVO.class);
                break;
            case GROUP:
                GroupInfo groupInfo = groupInfoMapper.selectByGroupId(contactId);
                if (groupInfo == null) {
                    return null;
                }
                resultVO.setNickName(groupInfo.getGroupName());
                break;
        }
        resultVO.setContactType(typeEnum.toString());
        resultVO.setContactId(contactId);
        //如果查的是自己
        if (userId.equals(contactId)) {
            resultVO.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            return resultVO;
        }

        //查询是否是好友
        UserContact userContact = userContactMapper.selectByUserIdAndContactId(userId, contactId);
        resultVO.setStatus(userContact == null ? null : userContact.getStatus());
        return resultVO;
    }

    /**
     * 申请好友
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer apply(TokenUserInfoDto tokenUserInfoDto, String contactId, String applyInfo) {
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(contactId);
        if (contactTypeEnum == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        //申请人
        String applyUserId = tokenUserInfoDto.getUserId();
        //默认申请信息
        applyInfo = StringUtils.isEmpty(applyInfo) ?
                String.format(Constants.APPLY_INFO_TEMPLATE, tokenUserInfoDto.getNickName()) : applyInfo;

        Long curTime = System.currentTimeMillis();
        Integer joinType;
        String receiveUserId = contactId;

        //查询对方好友是否已经添加，拉黑了就加不了了
        UserContact userContact = userContactMapper.selectByUserIdAndContactId(applyUserId, receiveUserId);
        if (userContact != null && ArrayUtils.contains(
                new Integer[]{UserContactStatusEnum.BLACKLIST_BE.getStatus(), UserContactStatusEnum.BLACKLIST_BE_FIRST.getStatus()}
                , userContact.getStatus())) {
            throw new BusinessException("对方已将您拉黑");
        }

        //查询被添加情况
        if (UserContactTypeEnum.GROUP == contactTypeEnum) {
            GroupInfo groupInfo = groupInfoMapper.selectByGroupId(contactId);
            if (groupInfo == null || GroupStatusEnum.DISSOLUTION.getStatus().equals(groupInfo.getStatus())) {
                throw new BusinessException("群聊不存在或已解散");
            }

            receiveUserId = groupInfo.getGroupOwnId();
            joinType = groupInfo.getJoinType();
        } else {
            UserInfo userInfo = userInfoMapper.selectByUserId(contactId);
            if (userInfo == null) {
                throw new BusinessException("联系人不存在");
            }
            joinType = userInfo.getJoinType();
        }

        //不需要申请
        if (JoinTypeEnum.PASS.getType().equals(joinType)) {
            addContact(applyUserId, receiveUserId, contactId, contactTypeEnum.getType(), applyInfo);
            return joinType;
        }

        UserContactApply dbApply = userContactApplyMapper.selectByApplyUserIdAndReceiveUserIdAndContactId(applyUserId, receiveUserId, contactId);
        //1.第一次申请
        if (dbApply == null) {
            UserContactApply apply = new UserContactApply();
            apply.setApplyUserId(applyUserId);
            apply.setContactId(contactId);
            apply.setReceiveUserId(receiveUserId);
            apply.setContactType(contactTypeEnum.getType());
            apply.setLastApplyTime(curTime);
            apply.setStatus(UserContactApplyStatusEnum.INIT.getStatus());
            apply.setApplyInfo(applyInfo);
            userContactApplyMapper.insert(apply);
        } else {
            //2.再次申请
            dbApply.setStatus(UserContactApplyStatusEnum.INIT.getStatus());
            dbApply.setLastApplyTime(curTime);
            dbApply.setApplyInfo(applyInfo);
            userContactApplyMapper.updateByApplyId(dbApply, dbApply.getApplyId());
        }

        if (dbApply == null || UserContactApplyStatusEnum.INIT.getStatus().equals(dbApply.getStatus())) {
            //发送ws消息
            MessageSendDTO<String> messageSendDTO = new MessageSendDTO<>();
            messageSendDTO.setMessageType(MessageTypeEnum.CONTACT_APPLY.getType());
            messageSendDTO.setMessageContent(applyInfo);
            messageSendDTO.setContactId(receiveUserId);
            messageHandler.sendMessage(messageSendDTO);
        }
        return joinType;
    }

    /**
     * 添加联系人
     */
    @Override
    public void addContact(String applyUserId, String receiveUserId, String contactId, Integer contactType, String applyInfo) {
        //群聊人数
        if (UserContactTypeEnum.GROUP.getType().equals(contactType)) {
            UserContactQuery query = new UserContactQuery();
            query.setContactId(contactId);
            query.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            Integer count = userContactMapper.selectCount(query);
            SysSettingDto sysSettingDto = redisComponent.getSysSetting();
            if (count >= sysSettingDto.getMaxGroupMemberCount()) {
                throw new BusinessException("成员已满");
            }
        }

        Date curDate = new Date();
        //同意 双方添加好友
        List<UserContact> contactList = new ArrayList<>();
        //申请人添加对方
        UserContact contactA = new UserContact();
        contactA.setUserId(applyUserId);
        contactA.setContactId(contactId);
        contactA.setContactType(contactType);
        contactA.setCreateTime(curDate);
        contactA.setUpdateTime(curDate);
        contactA.setStatus(UserContactStatusEnum.FRIEND.getStatus());
        contactList.add(contactA);
        //如果是好友 接收人添加申请人 群组不用
        if (UserContactTypeEnum.USER.getType().equals(contactType)) {
            UserContact contactB = new UserContact();
            contactB.setUserId(receiveUserId);
            contactB.setContactId(applyUserId);
            contactB.setContactType(contactType);
            contactB.setCreateTime(curDate);
            contactB.setUpdateTime(curDate);
            contactB.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            contactList.add(contactB);
        }
        userContactMapper.insertBatch(contactList);
        if (UserContactTypeEnum.USER.getType().equals(contactType)) {
            redisComponent.saveContact(receiveUserId, applyUserId);
        }
        redisComponent.saveContact(applyUserId, contactId);
        // 创建会话 发送消息
        String sessionId = null;
        if (UserContactTypeEnum.USER.getType().equals(contactType)) {
            sessionId = StringUtils.getChatSessionId4User(new String[]{applyUserId, receiveUserId});
        } else {
            sessionId = StringUtils.getChatSessionId4Group(contactId);
        }

        List<ChatSessionUser> chatSessionUserList = new ArrayList<>();
        if (UserContactTypeEnum.USER.getType().equals(contactType)) {
            ChatSession chatSession = new ChatSession();
            chatSession.setSessionId(sessionId);
            chatSession.setLastMessage(applyInfo);
            chatSession.setLastReceiveTime(curDate.getTime());
            chatSessionMapper.insertOrUpdate(chatSession);

            //申请人session
            ChatSessionUser applySessionUser = new ChatSessionUser();
            applySessionUser.setUserId(applyUserId);
            applySessionUser.setContactId(contactId);
            applySessionUser.setSessionId(sessionId);
            UserInfo receiveUser = userInfoMapper.selectByUserId(contactId);
            applySessionUser.setContactName(receiveUser.getNickName());
            chatSessionUserList.add(applySessionUser);

            //接收人session
            ChatSessionUser receiveSessionUser = new ChatSessionUser();
            receiveSessionUser.setUserId(contactId);
            receiveSessionUser.setContactId(applyUserId);
            receiveSessionUser.setSessionId(sessionId);
            UserInfo applyUser = userInfoMapper.selectByUserId(applyUserId);
            receiveSessionUser.setContactName(applyUser.getNickName());
            chatSessionUserList.add(receiveSessionUser);

            chatSessionUserMapper.insertBatch(chatSessionUserList);

            //记录消息
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSessionId(sessionId);
            chatMessage.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());
            chatMessage.setMessageContent(applyInfo);
            chatMessage.setSendUserId(applyUserId);
            chatMessage.setSendUserNickName(applyUser.getNickName());
            chatMessage.setSendTime(curDate.getTime());
            chatMessage.setContactId(contactId);
            chatMessage.setContactType(UserContactTypeEnum.USER.getType());
            chatMessageMapper.insert(chatMessage);

            MessageSendDTO messageSendDTO = CopyUtils.copy(chatMessage, MessageSendDTO.class);
            // 发送给接受好友申请的人
            messageHandler.sendMessage(messageSendDTO);

            // 发送给申请人 发送端为接受人 接受端为申请人 创建会话消息
            messageSendDTO.setMessageType(MessageTypeEnum.ADD_FRIEND_SELF.getType());
            messageSendDTO.setContactId(applyUserId);
            messageSendDTO.setExtentData(receiveUser);
            messageHandler.sendMessage(messageSendDTO);
        } else {
            // 1.申请人加入群组
            ChatSessionUser chatSessionUser = new ChatSessionUser();
            chatSessionUser.setUserId(applyUserId);
            chatSessionUser.setContactId(contactId);
            chatSessionUser.setSessionId(sessionId);
            GroupInfo groupInfo = groupInfoMapper.selectByGroupId(contactId);
            chatSessionUser.setContactName(groupInfo.getGroupName());
            chatSessionUserMapper.insertOrUpdate(chatSessionUser);

            // 5.1群组添加到联系人联系人缓存中
            redisComponent.saveContact(applyUserId, groupInfo.getGroupId());
            // 5.2将联系人通道添加到群组通道
            channelContextUtils.addUser2Group(applyUserId, groupInfo.getGroupId());


            UserInfo applyUserInfo = userInfoMapper.selectByUserId(applyUserId);
            String sendMessage = String.format(MessageTypeEnum.ADD_GROUP.getInitMessage(), applyUserInfo.getNickName());

            // 2 增加 Session 信息
            ChatSession chatSession = new ChatSession();
            chatSession.setSessionId(sessionId);
            chatSession.setLastMessage(sendMessage);
            chatSession.setLastReceiveTime(curDate.getTime());
            chatSessionMapper.insertOrUpdate(chatSession);

            // 3. 增加聊天信息
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSessionId(sessionId);
            chatMessage.setMessageType(MessageTypeEnum.ADD_GROUP.getType());
            chatMessage.setMessageContent(sendMessage);
            chatMessage.setSendUserId(null);
            chatMessage.setSendUserNickName(null);
            chatMessage.setSendTime(curDate.getTime());
            chatMessage.setContactId(contactId);
            chatMessage.setContactType(UserContactTypeEnum.GROUP.getType());
            chatMessage.setStatus(MessageStatusEnum.SENT.getStatus());
            chatMessageMapper.insert(chatMessage);



            // 4. 发送群消息
            MessageSendDTO messageSendDTO = CopyUtils.copy(chatMessage, MessageSendDTO.class);
            // 获取群人数量
            UserContactQuery userContactQuery = new UserContactQuery();
            userContactQuery.setContactId(contactId);
            userContactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            Integer count = userContactMapper.selectCount(userContactQuery);
            messageSendDTO.setMemberCount(count);
            messageSendDTO.setContactName(groupInfo.getGroupName());
            messageHandler.sendMessage(messageSendDTO);


        }
    }

    /**
     * 获取联系人列表
     */
    @Override
    public List<UserContact> loadContact(String userId, String contactType) {
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByName(contactType);
        if (contactTypeEnum == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserContactQuery query = new UserContactQuery();
        query.setUserId(userId);
        query.setContactType(contactTypeEnum.getType());
        if (UserContactTypeEnum.USER == contactTypeEnum) {
            query.setQueryContactInfo(true);
        } else {
            query.setQueryMemberCount(true);
            query.setQueryGroupInfo(true);
            query.setQueryExcludeMyGroup(true);
        }
        query.setOrderBy("create_time desc");
        query.setStatusArray(new Integer[]{
                UserContactStatusEnum.FRIEND.getStatus(),
                UserContactStatusEnum.DEL_BE.getStatus(),
                UserContactStatusEnum.BLACKLIST_BE.getStatus()
        });

        return findListByParam(query);
    }

    /**
     * 更改联系人类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeContactType(String userId, String contactId, Integer status) {
        UserContact contact = userContactMapper.selectByUserIdAndContactId(userId, contactId);
        if (contact == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //1.移除好友
        UserContact userContact = new UserContact();
        userContact.setStatus(status);
        userContact.setUpdateTime(new Date());
        userContactMapper.updateByUserIdAndContactId(userContact, userId, contactId);
        //2.更新被删除好友状态
        UserContact friend = new UserContact();
        if (status.equals(UserContactStatusEnum.DEL.getStatus())) {
            friend.setStatus(UserContactStatusEnum.DEL_BE.getStatus());
        }
        if (status.equals(UserContactStatusEnum.BLACKLIST.getStatus())) {
            friend.setStatus(UserContactStatusEnum.BLACKLIST_BE.getStatus());
        }
        friend.setUpdateTime(new Date());
        userContactMapper.updateByUserIdAndContactId(friend, contactId, userId);


        redisComponent.removeUserContact(userId, contactId);
        redisComponent.removeUserContact(contactId, userId);

    }

    /**
     * 添加机器人为好友
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRobotContact(String userId) {
        Date curDate = new Date();
        SysSettingDto sysSettingDto = redisComponent.getSysSetting();
        String contactId = sysSettingDto.getRobotUid();
        String contactName = sysSettingDto.getRobotNickName();
        String sendMessage = sysSettingDto.getRobotWelcome();
        sendMessage = StringUtils.cleanHtmlTag(sendMessage);

        //添加机器人好友
        UserContact userContact = new UserContact();
        userContact.setUserId(userId);
        userContact.setContactId(contactId);
        userContact.setContactName(contactName);
        userContact.setContactType(UserContactTypeEnum.USER.getType());
        userContact.setCreateTime(curDate);
        userContact.setUpdateTime(curDate);
        userContact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
        userContactMapper.insert(userContact);
        //增加会话信息
        String sessionId = StringUtils.getChatSessionId4User(new String[]{userId, contactId});
        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId(sessionId);
        chatSession.setLastMessage(sendMessage);
        chatSession.setLastReceiveTime(curDate.getTime());
        chatSessionMapper.insert(chatSession);

        //添加会话人信息
        ChatSessionUser chatSessionUser = new ChatSessionUser();
        chatSessionUser.setUserId(userId);
        chatSessionUser.setContactId(contactId);
        chatSessionUser.setSessionId(sessionId);
        chatSessionUser.setContactName(contactName);
        chatSessionUserMapper.insert(chatSessionUser);

        //增加聊天消息
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(sessionId);
        chatMessage.setMessageType(MessageTypeEnum.CHAT.getType());
        chatMessage.setMessageContent(sendMessage);
        chatMessage.setSendUserId(contactId);
        chatMessage.setContactId(userId);
        chatMessage.setSendUserNickName(contactName);
        chatMessage.setSendTime(curDate.getTime());
        chatMessage.setContactType(UserContactTypeEnum.USER.getType());
        chatMessage.setStatus(MessageStatusEnum.SENT.getStatus());
        chatMessageMapper.insert(chatMessage);

    }


}
