package top.enderherman.wetalk.service.impl;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
        contactId = StringUtils.isEmpty(contactId) ? "" : contactId.trim();
        UserInfo userInfo;
        UserContactTypeEnum typeEnum;
        if (contactId.contains("@")) {
            userInfo = userInfoMapper.selectPublicContactByEmail(contactId);
            typeEnum = UserContactTypeEnum.USER;
        } else {
            typeEnum = UserContactTypeEnum.getByPrefix(contactId);
            if (typeEnum == null) {
                return null;
            }
            userInfo = typeEnum == UserContactTypeEnum.USER
                    ? userInfoMapper.selectPublicContactByUserId(contactId)
                    : null;
        }
        if (typeEnum == UserContactTypeEnum.USER) {
            if (!isSearchableUser(userInfo)) {
                return null;
            }
            contactId = userInfo.getUserId();
        }
        UserContactSearchResultVO resultVO = new UserContactSearchResultVO();
        switch (typeEnum) {
            case USER:
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
        resultVO.setStatus(getContactSearchStatus(userId, contactId));
        if (typeEnum == UserContactTypeEnum.USER) resultVO.setRemark(getContactRemark(userId, contactId));
        return resultVO;
    }

    @Override
    public List<UserContactSearchResultVO> searchContactsByKeyword(String userId, String keyword) {
        List<UserContactSearchResultVO> results = new ArrayList<>();
        String normalizedKeyword = StringUtils.isEmpty(keyword) ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty() || normalizedKeyword.length() > 254) {
            return results;
        }

        if (normalizedKeyword.contains("@") || isContactId(normalizedKeyword)) {
            String exactKeyword = isContactId(normalizedKeyword)
                    ? Character.toUpperCase(normalizedKeyword.charAt(0)) + normalizedKeyword.substring(1)
                    : normalizedKeyword;
            UserContactSearchResultVO exactResult = searchContact(userId, exactKeyword);
            if (exactResult != null) {
                results.add(exactResult);
            }
            return results;
        }

        List<UserInfo> users = userInfoMapper.selectActiveContactsByNicknameFuzzy(
                escapeLikeKeyword(normalizedKeyword), 0, 10);
        if (users != null) {
            for (UserInfo user : users) {
                results.add(toUserSearchResult(userId, user));
            }
        }

        List<GroupInfo> groups = groupInfoMapper.selectActiveGroupsByNameFuzzy(
                escapeLikeKeyword(normalizedKeyword), 0, 10);
        if (groups != null) {
            for (GroupInfo group : groups) {
                results.add(toGroupSearchResult(userId, group));
            }
        }

        return results;
    }

    private boolean isContactId(String value) {
        if (value.length() < 2) return false;
        char prefix = Character.toUpperCase(value.charAt(0));
        return (prefix == 'U' || prefix == 'G') && value.substring(1).matches("\\d+");
    }

    private boolean isSearchableUser(UserInfo userInfo) {
        return userInfo != null
                && Integer.valueOf(UserStatusEnum.ENABLE.getStatus()).equals(userInfo.getStatus())
                && Integer.valueOf(0).equals(userInfo.getIsDelete());
    }

    private String escapeLikeKeyword(String value) {
        return value.replace("=", "==").replace("%", "=%").replace("_", "=_");
    }

    private UserContactSearchResultVO toUserSearchResult(String userId, UserInfo userInfo) {
        UserContactSearchResultVO result = CopyUtils.copy(userInfo, UserContactSearchResultVO.class);
        result.setContactId(userInfo.getUserId());
        result.setContactType(UserContactTypeEnum.USER.toString());
        result.setStatus(getContactSearchStatus(userId, userInfo.getUserId()));
        result.setRemark(getContactRemark(userId, userInfo.getUserId()));
        return result;
    }

    private UserContactSearchResultVO toGroupSearchResult(String userId, GroupInfo groupInfo) {
        UserContactSearchResultVO result = new UserContactSearchResultVO();
        result.setContactId(groupInfo.getGroupId());
        result.setContactType(UserContactTypeEnum.GROUP.toString());
        result.setNickName(groupInfo.getGroupName());
        result.setStatus(getContactSearchStatus(userId, groupInfo.getGroupId()));
        return result;
    }

    private Integer getContactSearchStatus(String userId, String contactId) {
        if (userId.equals(contactId)) {
            return UserContactStatusEnum.FRIEND.getStatus();
        }
        UserContact userContact = userContactMapper.selectByUserIdAndContactId(userId, contactId);
        return userContact == null ? null : userContact.getStatus();
    }

    private String getContactRemark(String userId, String contactId) {
        UserContact relationship = userContactMapper.selectByUserIdAndContactId(userId, contactId);
        return relationship == null ? null : relationship.getRemark();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public java.util.Map<String, String> saveRemark(String userId, String contactId, String remark) {
        if (userId == null || userId.equals(contactId)
                || UserContactTypeEnum.getByPrefix(contactId) != UserContactTypeEnum.USER || remark == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String normalized = remark.trim();
        if (normalized.length() > 40) throw new BusinessException(ResponseCodeEnum.CODE_600);
        UserContact own = userContactMapper.selectByUserIdAndContactId(userId, contactId);
        UserContact peer = userContactMapper.selectByUserIdAndContactId(contactId, userId);
        if (!isFriend(own) || !isFriend(peer) || !isSearchableUser(userInfoMapper.selectByUserId(contactId))) {
            throw new BusinessException("只能为当前好友设置备注");
        }
        java.util.Map<String, String> result = java.util.Map.of("contactId", contactId, "remark", normalized);
        if (normalized.equals(own.getRemark() == null ? "" : own.getRemark())) return result;
        Integer updated = userContactMapper.updateRemark(userId, contactId, normalized.isEmpty() ? null : normalized);
        if (updated == null || updated == 0) throw new BusinessException("好友关系已变化，请刷新后重试");
        MessageSendDTO<java.util.Map<String, String>> event = new MessageSendDTO<>();
        event.setMessageType(MessageTypeEnum.CONTACT_REMARK.getType());
        event.setContactId(userId);
        event.setContactType(UserContactTypeEnum.USER.getType());
        event.setSendTime(System.currentTimeMillis());
        event.setExtentData(result);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { messageHandler.sendMessage(event); }
            });
        } else {
            messageHandler.sendMessage(event);
        }
        return result;
    }

    private boolean isFriend(UserContact relationship) {
        return relationship != null && UserContactStatusEnum.FRIEND.getStatus().equals(relationship.getStatus())
                && UserContactTypeEnum.USER.getType().equals(relationship.getContactType());
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
        if (applyUserId.equals(contactId) || (applyInfo != null && applyInfo.length() > 100)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
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
            if (!isSearchableUser(userInfo)) {
                throw new BusinessException("联系人不存在");
            }
            UserContact reverse = userContactMapper.selectByUserIdAndContactId(contactId, applyUserId);
            if (reverse != null && UserContactStatusEnum.BLACKLIST.getStatus().equals(reverse.getStatus())) {
                throw new BusinessException("对方已将您拉黑");
            }
            joinType = userInfo.getJoinType();
        }

        if (userContact != null && UserContactStatusEnum.FRIEND.getStatus().equals(userContact.getStatus())) {
            return JoinTypeEnum.PASS.getType();
        }
        if (JoinTypeEnum.getByType(joinType) == null) throw new BusinessException(ResponseCodeEnum.CODE_600);

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
    @Transactional(rollbackFor = Exception.class)
    public void addContact(String applyUserId, String receiveUserId, String contactId, Integer contactType, String applyInfo) {
        boolean groupContact = UserContactTypeEnum.GROUP.getType().equals(contactType);
        if ((!groupContact && !UserContactTypeEnum.USER.getType().equals(contactType))
                || UserContactTypeEnum.getByPrefix(contactId) != (groupContact ? UserContactTypeEnum.GROUP : UserContactTypeEnum.USER)
                || applyUserId == null || applyUserId.equals(contactId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserInfo applicant = userInfoMapper.selectByUserId(applyUserId);
        if (!isSearchableUser(applicant)) throw new BusinessException("联系人不存在或已停用");
        UserContact existing = userContactMapper.selectByUserIdAndContactId(applyUserId, contactId);
        int currentGroupMemberCount = 0;
        if (groupContact) {
            GroupInfo group = groupInfoMapper.selectByGroupIdForUpdate(contactId);
            if (group == null || !GroupStatusEnum.NORMAL.getStatus().equals(group.getStatus())
                    || (receiveUserId != null && !receiveUserId.equals(group.getGroupOwnId()))) {
                throw new BusinessException("群聊不存在或已解散");
            }
            // 当前读与群行锁覆盖直接加入、申请批准和群主邀请，防止并发超员/重复入群。
            List<UserContact> members = userContactMapper.selectActiveGroupMembersForUpdate(contactId);
            if (members.stream().anyMatch(member -> applyUserId.equals(member.getUserId()))) return;
            currentGroupMemberCount = members.size();
        } else {
            UserInfo recipient = userInfoMapper.selectByUserId(contactId);
            if (!contactId.equals(receiveUserId) || !isSearchableUser(recipient)) {
                throw new BusinessException("联系人不存在或已停用");
            }
            UserContact reverse = userContactMapper.selectByUserIdAndContactId(contactId, applyUserId);
            if ((reverse != null && UserContactStatusEnum.BLACKLIST.getStatus().equals(reverse.getStatus()))
                    || (existing != null && ArrayUtils.contains(new Integer[]{
                    UserContactStatusEnum.BLACKLIST_BE.getStatus(), UserContactStatusEnum.BLACKLIST_BE_FIRST.getStatus()}, existing.getStatus()))) {
                throw new BusinessException("对方已将您拉黑");
            }
            if (existing != null && reverse != null
                    && UserContactStatusEnum.FRIEND.getStatus().equals(existing.getStatus())
                    && UserContactStatusEnum.FRIEND.getStatus().equals(reverse.getStatus())) return;
        }
        //群聊人数
        if (UserContactTypeEnum.GROUP.getType().equals(contactType)) {
            SysSettingDto sysSettingDto = redisComponent.getSysSetting();
            if (currentGroupMemberCount >= sysSettingDto.getMaxGroupMemberCount()) {
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
        userContactMapper.insertOrUpdateBatch(contactList);
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

            chatSessionUserMapper.insertOrUpdateBatch(chatSessionUserList);

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
            chatMessage.setStatus(MessageStatusEnum.SENT.getStatus());
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
            chatSessionUserMapper.updateLastReadMessageId(applyUserId, sessionId, chatMessage.getMessageId());



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
        if (UserContactTypeEnum.getByPrefix(contactId) != UserContactTypeEnum.USER
                || userId.equals(contactId)
                || (!UserContactStatusEnum.DEL.getStatus().equals(status)
                && !UserContactStatusEnum.BLACKLIST.getStatus().equals(status))) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
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
        UserContact reverse = userContactMapper.selectByUserIdAndContactId(contactId, userId);
        // 对方主动拉黑的决定不能由当前用户的删除/拉黑动作覆盖。
        if (reverse == null || !UserContactStatusEnum.BLACKLIST.getStatus().equals(reverse.getStatus())) {
            userContactMapper.updateByUserIdAndContactId(friend, contactId, userId);
        }


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
