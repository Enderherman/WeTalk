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


    public static final ConcurrentHashMap<String, Channel> USER_CONTEXT_MAP = new ConcurrentHashMap<>();

    public static final ConcurrentHashMap<String, ChannelGroup> GROUP_CONTEXT_MAP = new ConcurrentHashMap<>();


    /**
     * 添加联系人
     */
    public void addContext(String userId, Channel channel) {
        String channelId = channel.id().toString();
        AttributeKey attributeKey = null;
        if (!AttributeKey.exists(channelId)) {
            attributeKey = AttributeKey.newInstance(channelId);
        } else {
            attributeKey = AttributeKey.valueOf(channelId);
        }
        channel.attr(attributeKey).set(userId);

        //群聊
        List<String> contactList = redisComponent.getUserContactList(userId);
        for (String contact : contactList) {
            if (contact.startsWith(UserContactTypeEnum.GROUP.getPrefix())) {
                addUserToGroup(contact, channel);
            }
        }
        //用户
        USER_CONTEXT_MAP.put(userId, channel);
        redisComponent.saveUserHeartBeat(userId);

        //查询用户最后登录时间
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        // 给用户发送消息 获取用户最后离线时间
        Long sourceLastOfTime = userInfo.getLastOffTime();
        Long lastOfTime = sourceLastOfTime;
        //更新用户最后登录时间
        userInfo.setLastLoginTime(new Date());
        userInfoMapper.updateByUserId(userInfo, userId);

        // 如果时间太久，只取最近三天的消息数 TODO 我觉这里这么操作师错误的 应该是获取上次离线到目前的消息
        if (sourceLastOfTime != null && System.currentTimeMillis() - Constants.MILLISECONDS_THREE_DAY > sourceLastOfTime) {
            lastOfTime = Constants.MILLISECONDS_THREE_DAY;
        }
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
        sendMessage(messageSendDTO,userId);

    }

    /**
     * 增加群到会话中
     */
    public void addUser2Group(String userId, String groupId) {
        Channel channel = USER_CONTEXT_MAP.get(userId);
        addUserToGroup(groupId, channel);
    }


    /**
     * 增加群
     */
    private void addUserToGroup(String groupId, Channel channel) {
        ChannelGroup group = GROUP_CONTEXT_MAP.get(groupId);
        if (group == null) {
            group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
            GROUP_CONTEXT_MAP.put(groupId, group);
        }
        if (channel == null) {
            return;
        }
        group.add(channel);
    }


    /**
     * 断开连接
     */
    public void removeContext(Channel channel) {
        Attribute<String> attributeKey = channel.attr(AttributeKey.valueOf(channel.id().toString()));
        String userId = attributeKey.get();
        if (!StringUtils.isEmpty(userId)) {
            USER_CONTEXT_MAP.remove(userId);
        }
        redisComponent.removeUserHeartBeat(userId);
        // 更新用户最后离线时间
        UserInfo userInfo = new UserInfo();
        userInfo.setLastOffTime(System.currentTimeMillis());
        userInfoMapper.updateByUserId(userInfo, userId);
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
        Channel channel = USER_CONTEXT_MAP.get(userId);
        if (channel == null) {
            return;
        }
        channel.close();
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
            Channel channel = USER_CONTEXT_MAP.get(userId);
            if (channel == null) {
                return;
            }
            group.remove(channel);
        }
        //解散群聊
        if (messageTypeEnum == MessageTypeEnum.DISSOLUTION_GROUP) {
            GROUP_CONTEXT_MAP.remove(messageSendDTO.getContactId());
            group.close();
        }
    }


    /**
     * 发送消息
     */
    public void sendMessage(MessageSendDTO<?> messageSendDTO, String receiveId) {
        if (receiveId == null) {
            return;
        }
        Channel sendChannel = USER_CONTEXT_MAP.get(receiveId);
        if (sendChannel == null) {
            return;
        }
        // 相对于客户端而言 联系人就是发送人 所以转换一下再发送 好友申请时不 处理
        if (MessageTypeEnum.ADD_FRIEND_SELF.getType().equals(messageSendDTO.getMessageType())) {
            //从ExtentDATA中取出来 接收人的用户信息 加载到发送人的客户端
            UserInfo userInfo = (UserInfo) messageSendDTO.getExtentData();
            messageSendDTO.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());
            messageSendDTO.setContactId(userInfo.getUserId());
            messageSendDTO.setContactName(userInfo.getNickName());
            messageSendDTO.setExtentData(null);
        } else {
            //接收人refresh消息
            messageSendDTO.setContactId(messageSendDTO.getSendUserId());
            messageSendDTO.setContactName(messageSendDTO.getSendUserNickName());
        }
        //服务端给客户端发送一条消息？
        sendChannel.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(messageSendDTO)));
    }


}
