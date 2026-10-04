package top.enderherman.wetalk.webSocket;

import cn.hutool.json.JSONUtil;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.enums.MessageTypeEnum;
import top.enderherman.wetalk.entity.enums.UserContactApplyStatusEnum;
import top.enderherman.wetalk.entity.enums.UserContactTypeEnum;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.po.ChatSessionUser;
import top.enderherman.wetalk.entity.po.UserContactApply;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.entity.query.ChatSessionUserQuery;
import top.enderherman.wetalk.entity.query.UserContactApplyQuery;
import top.enderherman.wetalk.entity.query.UserInfoQuery;
import top.enderherman.wetalk.entity.vo.WsInitDataVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.ChatMessageMapper;
import top.enderherman.wetalk.mappers.ChatSessionUserMapper;
import top.enderherman.wetalk.mappers.UserContactApplyMapper;
import top.enderherman.wetalk.mappers.UserInfoMapper;
import top.enderherman.wetalk.utils.StringUtils;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ChannelContextUtils {

    public static final AttributeKey<String> USER_ID = AttributeKey.valueOf("wetalk.userId");
    public static final AttributeKey<String> SESSION_ID = AttributeKey.valueOf("wetalk.sessionId");
    public static final AttributeKey<String> CONNECTION_ID = AttributeKey.valueOf("wetalk.connectionId");
    private static final AttributeKey<Long> LAST_SESSION_TOUCH_AT = AttributeKey.valueOf("wetalk.lastSessionTouchAt");

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

    @Resource
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Resource
    private UserContactApplyMapper<UserContactApply, UserContactApplyQuery> userContactApplyMapper;


    public static final ConcurrentHashMap<String, ConcurrentHashMap<String, Channel>> USER_CONTEXT_MAP = new ConcurrentHashMap<>();

    public static final ConcurrentHashMap<String, ChannelGroup> GROUP_CONTEXT_MAP = new ConcurrentHashMap<>();


    /**
     * 添加联系人
     */
    public void addContext(String userId, Channel channel) {
        addContext(userId, null, channel);
    }

    public void addContext(TokenUserInfoDto session, Channel channel) {
        if (session == null) return;
        addContext(session.getUserId(), session.getSessionId(), channel);
    }

    private void addContext(String userId, String sessionId, Channel channel) {
        channel.attr(USER_ID).set(userId);
        channel.attr(SESSION_ID).set(sessionId);
        channel.attr(CONNECTION_ID).set(java.util.UUID.randomUUID().toString());
        channel.attr(LAST_SESSION_TOUCH_AT).set(System.currentTimeMillis());

        //群聊
        List<String> contactList = redisComponent.getUserContactList(userId);
        if (contactList == null) contactList = List.of();
        for (String contact : contactList) {
            if (contact.startsWith(UserContactTypeEnum.GROUP.getPrefix())) {
                addUserToGroup(contact, channel);
            }
        }
        // 同一账号可以保留多个设备和浏览器连接。
        USER_CONTEXT_MAP.compute(userId, (key, current) -> {
            ConcurrentHashMap<String, Channel> channels = current == null ? new ConcurrentHashMap<>() : current;
            channels.put(channel.attr(CONNECTION_ID).get(), channel);
            return channels;
        });
        redisComponent.saveUserHeartBeat(userId);

        //查询用户最后登录时间
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        // 给用户发送消息 获取用户最后离线时间
        Long sourceLastOfTime = userInfo.getLastOffTime();
        Long lastOfTime = sourceLastOfTime;
        //更新用户最后登录时间
        userInfo.setLastLoginTime(new Date());
        userInfoMapper.updateByUserId(userInfo, userId);

        long cutoff = System.currentTimeMillis() - Constants.MILLISECONDS_THREE_DAY;
        lastOfTime = sourceLastOfTime == null ? cutoff : Math.max(sourceLastOfTime, cutoff);
        // 1.查询会话信息 查询用户所有的会话信息 保证换了设备会话同步
        ChatSessionUserQuery chatSessionUserQuery = new ChatSessionUserQuery();
        chatSessionUserQuery.setUserId(userId);
        chatSessionUserQuery.setOrderBy("last_receive_time desc");
        List<ChatSessionUser> chatSessionUserList = chatSessionUserMapper.selectList(chatSessionUserQuery);

        WsInitDataVO wsInitDataVO = new WsInitDataVO();
        wsInitDataVO.setChatSessionList(chatSessionUserList);

        // 2.查询聊天信息
        // 2.1查询所有联系人 我加入的群组 还有 以为我为联系人的单聊联系方式
        List<String> groupIdList = contactList.stream().filter(contact -> contact.startsWith(UserContactTypeEnum.GROUP.getPrefix())).collect(Collectors.toList());
        groupIdList.add(userId);
        // 2.2设置查询条件
        ChatMessageQuery chatMessageQuery = new ChatMessageQuery();
        chatMessageQuery.setContactIdList(groupIdList);
        chatMessageQuery.setLastReceiveTime(lastOfTime);
        chatMessageQuery.setOrderBy("message_id asc");
        List<ChatMessage> chatMessageList = chatMessageMapper.selectList(chatMessageQuery);
        wsInitDataVO.setChatMessageList(chatMessageList);

        // 3.查询好友申请
        UserContactApplyQuery aQuery = new UserContactApplyQuery();
        aQuery.setContactId(userId);
        aQuery.setStatus(UserContactApplyStatusEnum.INIT.getStatus());
        Integer count = userContactApplyMapper.selectCount(aQuery);
        wsInitDataVO.setApplyCount(count);


        // 4.发送消息
        MessageSendDTO<WsInitDataVO> messageSendDTO = new MessageSendDTO<>();
        messageSendDTO.setMessageType(MessageTypeEnum.INIT.getType());
        messageSendDTO.setContactId(userId);
        messageSendDTO.setExtentData(wsInitDataVO);
        sendMessage(messageSendDTO, channel);

    }

    /**
     * 增加群到会话中
     */
    public void addUser2Group(String userId, String groupId) {
        ConcurrentHashMap<String, Channel> userChannels = USER_CONTEXT_MAP.get(userId);
        if (userChannels == null) return;
        for (Channel channel : userChannels.values()) {
            addUserToGroup(groupId, channel);
        }
    }


    /**
     * 增加群
     */
    private void addUserToGroup(String groupId, Channel channel) {
        if (channel == null) return;
        ChannelGroup group = GROUP_CONTEXT_MAP.computeIfAbsent(groupId,
                key -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
        group.add(channel);
    }


    /**
     * 断开连接
     */
    public void removeContext(Channel channel) {
        String userId = channel.attr(USER_ID).get();
        if (StringUtils.isEmpty(userId)) return;
        USER_CONTEXT_MAP.computeIfPresent(userId, (key, channels) -> {
            String connectionId = channel.attr(CONNECTION_ID).get();
            if (connectionId == null || !channels.remove(connectionId, channel)) return channels;
            if (!channels.isEmpty()) return channels;
            redisComponent.removeUserHeartBeat(userId);
            UserInfo userInfo = new UserInfo();
            userInfo.setLastOffTime(System.currentTimeMillis());
            userInfoMapper.updateByUserId(userInfo, userId);
            return null;
        });
    }

    /**
     * 发送广播消息
     */
    public void sendMessage(MessageSendDTO<?> messageSendDTO) {
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(messageSendDTO.getContactId());
        if (contactTypeEnum != null) {
            switch (contactTypeEnum) {
                case USER:
                    sendToUser(messageSendDTO);
                    break;
                case GROUP:
                    sendToGroup(messageSendDTO);
                    break;
            }
        }
        else throw new BusinessException(ResponseCodeEnum.CODE_600);
    }

    /**
     * 发消息给用户
     */
    private void sendToUser(MessageSendDTO<?> messageSendDTO) {
        String contactId = messageSendDTO.getContactId();
        sendMessage(messageSendDTO, contactId);
        Integer type = messageSendDTO.getMessageType();
        String senderId = messageSendDTO.getSendUserId();
        boolean mirrorToSender = MessageTypeEnum.CHAT.getType().equals(type)
                || MessageTypeEnum.MEDIA_CHAT.getType().equals(type)
                || MessageTypeEnum.FILE_UPLOAD.getType().equals(type);
        if (mirrorToSender && !StringUtils.isEmpty(senderId) && !senderId.equals(contactId)
                && USER_CONTEXT_MAP.containsKey(senderId)) {
            // 发送者的另一端仍将收件人视为联系人，不能套用接收方的视角转换。
            MessageSendDTO<?> senderView = top.enderherman.wetalk.utils.CopyUtils.copy(messageSendDTO, MessageSendDTO.class);
            if (StringUtils.isEmpty(senderView.getContactName())) {
                ChatSessionUser ownSession = chatSessionUserMapper.selectByUserIdAndContactId(senderId, contactId);
                if (ownSession != null && !StringUtils.isEmpty(ownSession.getContactName())) {
                    senderView.setContactName(ownSession.getContactName());
                }
            }
            // 只取真实会话名称，私有备注不进入其他账号共享的 DTO。
            sendMessage(senderView, senderId, false);
        }
        //强制下线
        if (MessageTypeEnum.FORCE_OFF_LINE.getType().equals(messageSendDTO.getMessageType())) {
            // 关闭通道
            closeContact(contactId);
        }
    }

    /**
     * 关闭通道
     */
    public void closeContact(String userId) {
        if (StringUtils.isEmpty(userId)) {
            return;
        }
        redisComponent.clearTokenUserInfoDto(userId);
        ConcurrentHashMap<String, Channel> channels = USER_CONTEXT_MAP.get(userId);
        if (channels != null) channels.values().forEach(Channel::close);
    }

    public void closeSession(String userId, String sessionId) {
        if (StringUtils.isEmpty(userId) || StringUtils.isEmpty(sessionId)) return;
        ConcurrentHashMap<String, Channel> channels = USER_CONTEXT_MAP.get(userId);
        if (channels == null) return;
        for (Channel channel : channels.values()) {
            if (sessionId.equals(channel.attr(SESSION_ID).get())) channel.close();
        }
    }

    public void touchSessionActivity(Channel channel) {
        String userId = channel.attr(USER_ID).get();
        String sessionId = channel.attr(SESSION_ID).get();
        if (StringUtils.isEmpty(userId) || StringUtils.isEmpty(sessionId)) return;
        long now = System.currentTimeMillis();
        Long lastTouch = channel.attr(LAST_SESSION_TOUCH_AT).get();
        if (lastTouch != null && now - lastTouch < 60_000) return;
        channel.attr(LAST_SESSION_TOUCH_AT).set(now);
        redisComponent.touchUserSession(userId, sessionId);
    }

    /**
     * 发消息给群聊
     */
    private void sendToGroup(MessageSendDTO<?> messageSendDTO) {
        if (messageSendDTO.getContactId() == null) {
            return;
        }
        ChannelGroup group = GROUP_CONTEXT_MAP.get(messageSendDTO.getContactId());
        if (group == null) {
            return;
        }
        group.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(messageSendDTO)));

        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(messageSendDTO.getMessageType());
        //退出群聊
        if (messageTypeEnum == MessageTypeEnum.LEAVE_GROUP || messageTypeEnum == MessageTypeEnum.REMOVE_GROUP) {
            String userId = (String) messageSendDTO.getExtentData();
            redisComponent.removeUserContact(userId, messageSendDTO.getContactId());
            ConcurrentHashMap<String, Channel> userChannels = USER_CONTEXT_MAP.get(userId);
            if (userChannels == null) return;
            for (Channel channel : userChannels.values()) group.remove(channel);
        }
        //解散群聊
        if (messageTypeEnum == MessageTypeEnum.DISSOLUTION_GROUP) {
            GROUP_CONTEXT_MAP.remove(messageSendDTO.getContactId());
            group.clear();
        }
    }


    /**
     * 发送消息
     */
    public void sendMessage(MessageSendDTO<?> messageSendDTO, String receiveId) {
        sendMessage(messageSendDTO, receiveId, true);
    }

    private void sendMessage(MessageSendDTO<?> messageSendDTO, String receiveId, boolean receiverPerspective) {
        if (receiveId == null) {
            return;
        }
        ConcurrentHashMap<String, Channel> userChannels = USER_CONTEXT_MAP.get(receiveId);
        if (userChannels == null || userChannels.isEmpty()) return;
        String payload = JSONUtil.toJsonStr(receiverPerspective ? prepareMessage(messageSendDTO) : messageSendDTO);
        for (Channel channel : userChannels.values()) {
            channel.writeAndFlush(new TextWebSocketFrame(payload));
        }
    }

    private void sendMessage(MessageSendDTO<?> messageSendDTO, Channel channel) {
        if (channel == null || !channel.isOpen()) return;
        channel.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(prepareMessage(messageSendDTO))));
    }

    private MessageSendDTO<?> prepareMessage(MessageSendDTO<?> messageSendDTO) {
        MessageSendDTO<?> delivered = top.enderherman.wetalk.utils.CopyUtils.copy(messageSendDTO, MessageSendDTO.class);
        // 相对于客户端而言 联系人就是发送人 所以转换一下再发送 好友申请时不 处理
        if (MessageTypeEnum.ADD_FRIEND_SELF.getType().equals(delivered.getMessageType())) {
            //从ExtentDATA中取出来 接收人的用户信息 加载到发送人的客户端
            UserInfo userInfo = (UserInfo) delivered.getExtentData();
            delivered.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());
            delivered.setContactId(userInfo.getUserId());
            delivered.setContactName(userInfo.getNickName());
            delivered.setExtentData(null);
        } else if (!StringUtils.isEmpty(delivered.getSendUserId())) {
            //接收人refresh消息
            delivered.setContactId(delivered.getSendUserId());
            delivered.setContactName(delivered.getSendUserNickName());
        }
        return delivered;
    }


}
