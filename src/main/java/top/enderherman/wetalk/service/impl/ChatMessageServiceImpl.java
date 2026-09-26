package top.enderherman.wetalk.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.enums.*;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.po.ChatSession;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.entity.query.ChatSessionQuery;
import top.enderherman.wetalk.entity.query.SimplePage;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.ChatMessageMapper;
import top.enderherman.wetalk.mappers.ChatSessionMapper;
import top.enderherman.wetalk.mappers.UserContactMapper;
import top.enderherman.wetalk.service.ChatMessageService;
import top.enderherman.wetalk.service.AiService;
import top.enderherman.wetalk.utils.CopyUtils;
import top.enderherman.wetalk.utils.DateUtils;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import jakarta.annotation.Resource;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import reactor.core.Disposable;


/**
 * 聊天消息表 业务接口实现
 */
@Slf4j
@Service("chatMessageService")
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ConcurrentMap<Integer, AiStreamState> activeAiStreams = new ConcurrentHashMap<>();

    private static final class AiStreamState {
        private final Integer messageId;
        private final String sessionId;
        private final String contactId;
        private final String robotId;
        private final String robotNickName;
        private final StringBuilder content = new StringBuilder();
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private volatile Disposable disposable;

        private AiStreamState(Integer messageId, String sessionId, String contactId, String robotId, String robotNickName) {
            this.messageId = messageId;
            this.sessionId = sessionId;
            this.contactId = contactId;
            this.robotId = robotId;
            this.robotNickName = robotNickName;
        }
    }

    @Resource
    private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

    @Resource
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private AppConfig appConfig;

    @Resource
    private AiService aiService;


    /**
     * 根据条件查询列表
     */
    @Override
    public List<ChatMessage> findListByParam(ChatMessageQuery param) {
        return this.chatMessageMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(ChatMessageQuery param) {
        return this.chatMessageMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<ChatMessage> findListByPage(ChatMessageQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<ChatMessage> list = this.findListByParam(param);
        PaginationResultVO<ChatMessage> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 按游标读取当前用户可以访问的会话历史，返回顺序统一为从旧到新。
     */
    @Override
    public PaginationResultVO<ChatMessage> loadHistory(TokenUserInfoDto userInfo, String contactId, Integer beforeMessageId, Integer pageSize) {
        UserContactTypeEnum contactType = UserContactTypeEnum.getByPrefix(contactId);
        if (contactType == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        List<String> userContactList = redisComponent.getUserContactList(userInfo.getUserId());
        if (userContactList == null || !userContactList.contains(contactId)) {
            throw new BusinessException(contactType == UserContactTypeEnum.USER
                    ? ResponseCodeEnum.CODE_902
                    : ResponseCodeEnum.CODE_903);
        }

        String sessionId = contactType == UserContactTypeEnum.USER
                ? StringUtils.getChatSessionId4User(new String[]{userInfo.getUserId(), contactId})
                : StringUtils.getChatSessionId4Group(contactId);
        ChatMessageQuery query = new ChatMessageQuery();
        query.setSessionId(sessionId);
        query.setBeforeMessageId(beforeMessageId);
        query.setOrderBy("message_id desc");
        query.setPageNo(1);
        query.setPageSize(pageSize == null ? 30 : Math.max(1, Math.min(pageSize, 50)));
        PaginationResultVO<ChatMessage> result = findListByPage(query);
        Collections.reverse(result.getList());
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(ChatMessage bean) {
        return this.chatMessageMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<ChatMessage> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.chatMessageMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<ChatMessage> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.chatMessageMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(ChatMessage bean, ChatMessageQuery param) {
        StringUtils.checkParam(param);
        return this.chatMessageMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(ChatMessageQuery param) {
        StringUtils.checkParam(param);
        return this.chatMessageMapper.deleteByParam(param);
    }

    /**
     * 根据MessageId获取对象
     */
    @Override
    public ChatMessage getChatMessageByMessageId(Integer messageId) {
        return this.chatMessageMapper.selectByMessageId(messageId);
    }

    /**
     * 根据MessageId修改
     */
    @Override
    public Integer updateChatMessageByMessageId(ChatMessage bean, Integer messageId) {
        return this.chatMessageMapper.updateByMessageId(bean, messageId);
    }

    /**
     * 根据MessageId删除
     */
    @Override
    public Integer deleteChatMessageByMessageId(Integer messageId) {
        return this.chatMessageMapper.deleteByMessageId(messageId);
    }

    /**
     * 发送消息
     */
    @Override
    public MessageSendDTO<?> saveMessage(ChatMessage chatMessage, TokenUserInfoDto userInfoDto) {
        //校验好友关系
        if (!Constants.ROBOT_UID.equals(userInfoDto.getUserId())) {
            List<String> userContactList = redisComponent.getUserContactList(userInfoDto.getUserId());
            if (userContactList == null || !userContactList.contains(chatMessage.getContactId())) {
                UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(chatMessage.getContactId());
                if (contactTypeEnum == UserContactTypeEnum.USER) {
                    throw new BusinessException(ResponseCodeEnum.CODE_902);
                } else {
                    throw new BusinessException(ResponseCodeEnum.CODE_903);
                }
            }
        }

        if (Constants.ROBOT_UID.equals(chatMessage.getContactId()) && !aiService.isEnabled()) {
            throw new BusinessException("AI service is disabled");
        }

        //1.会话信息
        String sessionId = null;
        String sendUserId = userInfoDto.getUserId();
        String contactId = chatMessage.getContactId();
        Long curTime = System.currentTimeMillis();
        //处理内容
        String messageContent = StringUtils.cleanHtmlTag(chatMessage.getMessageContent());
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(contactId);
        if (UserContactTypeEnum.USER == contactTypeEnum) {
            sessionId = StringUtils.getChatSessionId4User(new String[]{sendUserId, contactId});
        } else {
            sessionId = StringUtils.getChatSessionId4Group(contactId);
        }
        //发送状态
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(chatMessage.getMessageType());
        Integer status = MessageTypeEnum.MEDIA_CHAT == messageTypeEnum ? MessageStatusEnum.SENDING.getStatus() : MessageStatusEnum.SENT.getStatus();


        //更新会话
        ChatSession chatSession = new ChatSession();
        if (UserContactTypeEnum.USER != contactTypeEnum) {
            chatSession.setLastMessage(messageContent);
        } else {
            chatSession.setLastMessage(userInfoDto.getNickName() + ": " + messageContent);
        }
        chatSession.setSessionId(sessionId);
        chatSession.setLastReceiveTime(curTime);
        chatSessionMapper.updateBySessionId(chatSession, sessionId);

        //记录消息消息表
        chatMessage.setSendTime(curTime);
        chatMessage.setSessionId(sessionId);
        chatMessage.setStatus(status);
        chatMessage.setMessageContent(messageContent);
        chatMessage.setSendUserId(sendUserId);
        chatMessage.setSendUserNickName(userInfoDto.getNickName());
        chatMessage.setContactType(contactTypeEnum.getType());
        chatMessageMapper.insert(chatMessage);

        //发送ws消息
        MessageSendDTO<?> messageSendDTO = CopyUtils.copy(chatMessage, MessageSendDTO.class);
        if (Constants.ROBOT_UID.equals(contactId)) {
            SysSettingDto sysSettingDto = redisComponent.getSysSetting();
            TokenUserInfoDto robot = new TokenUserInfoDto();
            robot.setUserId(sysSettingDto.getRobotUid());
            robot.setNickName(sysSettingDto.getRobotNickName());

            // 创建初始AI消息
            ChatMessage robotMessage = new ChatMessage();
            robotMessage.setContactId(sendUserId);
            robotMessage.setMessageContent("");
            robotMessage.setMessageType(MessageTypeEnum.AI_CHAT.getType());

            // 设置会话信息
            String robotSessionId = StringUtils.getChatSessionId4User(new String[]{robot.getUserId(), sendUserId});
            robotMessage.setSendTime(System.currentTimeMillis());
            robotMessage.setSessionId(robotSessionId);
            robotMessage.setStatus(MessageStatusEnum.SENT.getStatus());
            robotMessage.setSendUserId(robot.getUserId());
            robotMessage.setSendUserNickName(robot.getNickName());
            robotMessage.setContactType(UserContactTypeEnum.USER.getType());

            // 先插入空消息到数据库
            chatMessageMapper.insert(robotMessage);

            Integer robotMessageId = robotMessage.getMessageId();
            MessageSendDTO<?> initSendDTO = CopyUtils.copy(robotMessage, MessageSendDTO.class);
            initSendDTO.setSendTime(System.currentTimeMillis());
            messageHandler.sendMessage(initSendDTO);
            // 使用流式响应处理AI回复
            startAiStream(messageContent, robotMessageId, robotSessionId, sendUserId,
                    robot.getUserId(), robot.getNickName());
        } else {
            messageHandler.sendMessage(messageSendDTO);
        }

        return messageSendDTO;
    }

    private void startAiStream(String prompt, Integer messageId, String sessionId, String contactId,
                               String robotId, String robotNickName) {
        AiStreamState state = new AiStreamState(messageId, sessionId, contactId, robotId, robotNickName);
        activeAiStreams.put(messageId, state);
        try {
            state.disposable = aiService.sendMsgFlow(prompt).subscribe(
                    content -> streamAiContent(state, content),
                    error -> {
                        log.warn("AI stream failed for message {}", messageId);
                        finishAiStream(state, MessageStatusEnum.AI_FAILED, false);
                    },
                    () -> finishAiStream(state, MessageStatusEnum.SENT, false));
            if (state.finished.get()) state.disposable.dispose();
        } catch (RuntimeException error) {
            log.warn("AI stream could not start for message {}", messageId);
            finishAiStream(state, MessageStatusEnum.AI_FAILED, false);
        }
    }

    private void streamAiContent(AiStreamState state, String content) {
        synchronized (state) {
            if (state.finished.get()) return;
            state.content.append(content);
            MessageSendDTO<?> streamingMessage = aiEndMessage(state, state.content.toString(), null);
            streamingMessage.setMessageType(MessageTypeEnum.AI_CHAT_STREAM.getType());
            messageHandler.sendMessage(streamingMessage);
        }
    }

    private MessageSendDTO<?> finishAiStream(AiStreamState state, MessageStatusEnum status, boolean cancelProvider) {
        synchronized (state) {
            if (!state.finished.compareAndSet(false, true)) {
                ChatMessage current = chatMessageMapper.selectByMessageId(state.messageId);
                Integer currentStatus = current == null ? MessageStatusEnum.AI_FAILED.getStatus() : current.getStatus();
                return aiEndMessage(state, current == null ? "" : current.getMessageContent(), currentStatus);
            }
            if (cancelProvider && state.disposable != null) state.disposable.dispose();

            String finalContent = state.content.toString();
            ChatMessage update = new ChatMessage();
            update.setMessageContent(finalContent);
            update.setStatus(status.getStatus());
            chatMessageMapper.updateByMessageId(update, state.messageId);

            ChatSession sessionUpdate = new ChatSession();
            sessionUpdate.setLastMessage(aiSessionPreview(state, finalContent, status));
            sessionUpdate.setLastReceiveTime(System.currentTimeMillis());
            chatSessionMapper.updateBySessionId(sessionUpdate, state.sessionId);

            activeAiStreams.remove(state.messageId, state);
            MessageSendDTO<?> result = aiEndMessage(state, finalContent, status.getStatus());
            messageHandler.sendMessage(result);
            return result;
        }
    }

    private String aiSessionPreview(AiStreamState state, String content, MessageStatusEnum status) {
        String preview = content;
        if (status == MessageStatusEnum.AI_CANCELLED) {
            preview = content.isEmpty() ? "AI 生成已停止" : content + "（已停止）";
        } else if (status == MessageStatusEnum.AI_FAILED) {
            preview = content.isEmpty() ? "AI 生成失败，请重试" : content + "（生成失败）";
        } else if (content.isEmpty()) {
            preview = "AI 没有返回文本";
        }
        return state.robotNickName + ": " + preview;
    }

    private MessageSendDTO<?> aiEndMessage(AiStreamState state, String content, Integer status) {
        MessageSendDTO<?> result = new MessageSendDTO<>();
        result.setMessageId(state.messageId);
        result.setSessionId(state.sessionId);
        result.setContactId(state.contactId);
        result.setContactType(UserContactTypeEnum.USER.getType());
        result.setSendUserId(state.robotId);
        result.setSendUserNickName(state.robotNickName);
        result.setMessageType(MessageTypeEnum.AI_CHAT_STREAM_END.getType());
        result.setMessageContent(content == null ? "" : content);
        result.setStatus(status);
        result.setSendTime(System.currentTimeMillis());
        return result;
    }

    @Override
    public MessageSendDTO<?> cancelAiMessage(Integer messageId, TokenUserInfoDto userInfoDto) {
        if (messageId == null || messageId < 1) throw new BusinessException(ResponseCodeEnum.CODE_600);
        ChatMessage message = chatMessageMapper.selectByMessageId(messageId);
        if (message == null || !MessageTypeEnum.AI_CHAT.getType().equals(message.getMessageType())
                || !Constants.ROBOT_UID.equals(message.getSendUserId())
                || !userInfoDto.getUserId().equals(message.getContactId())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        AiStreamState state = activeAiStreams.get(messageId);
        if (state != null) return finishAiStream(state, MessageStatusEnum.AI_CANCELLED, true);

        Integer status = message.getStatus();
        if (MessageStatusEnum.AI_CANCELLED.getStatus().equals(status)
                || MessageStatusEnum.AI_FAILED.getStatus().equals(status)
                || (MessageStatusEnum.SENT.getStatus().equals(status) && message.getMessageContent() != null
                && !message.getMessageContent().isEmpty())) {
            return persistedAiEndMessage(message);
        }

        // A placeholder without a live task is left by a process restart; finish it as failed.
        message.setStatus(MessageStatusEnum.AI_FAILED.getStatus());
        chatMessageMapper.updateByMessageId(message, messageId);
        ChatSession sessionUpdate = new ChatSession();
        sessionUpdate.setLastMessage(message.getSendUserNickName() + ": AI 生成失败，请重试");
        sessionUpdate.setLastReceiveTime(System.currentTimeMillis());
        chatSessionMapper.updateBySessionId(sessionUpdate, message.getSessionId());
        MessageSendDTO<?> result = persistedAiEndMessage(message);
        messageHandler.sendMessage(result);
        return result;
    }

    private MessageSendDTO<?> persistedAiEndMessage(ChatMessage message) {
        AiStreamState state = new AiStreamState(message.getMessageId(), message.getSessionId(), message.getContactId(),
                message.getSendUserId(), message.getSendUserNickName());
        return aiEndMessage(state, message.getMessageContent(), message.getStatus());
    }


    /**
     * 上传文件
     */
    @Override
    public void saveMessageFile(String userId, Integer messageId, MultipartFile file, MultipartFile cover) {
        ChatMessage chatMessage = chatMessageMapper.selectByMessageId(messageId);
        if (chatMessage == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (!chatMessage.getSendUserId().equals(userId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        SysSettingDto sysSettingDto = redisComponent.getSysSetting();
        String fileSuffix = StringUtils.getFileSuffix(file.getOriginalFilename());
        //校验文件夹大小
        if (!StringUtils.isEmpty(fileSuffix) &&
                ArrayUtils.contains(Constants.IMAGE_SUFFIX_LIST, fileSuffix.toLowerCase()) &&
                file.getSize() > Constants.FILE_SIZE_MB * sysSettingDto.getMaxImageSize()) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        } else if (!StringUtils.isEmpty(fileSuffix) &&
                ArrayUtils.contains(Constants.VIDEO_SUFFIX_LIST, fileSuffix.toLowerCase()) &&
                file.getSize() > Constants.FILE_SIZE_MB * sysSettingDto.getMaxVideoSize()) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        } else if (!ArrayUtils.contains(Constants.IMAGE_SUFFIX_LIST, fileSuffix.toLowerCase()) &&
                !ArrayUtils.contains(Constants.VIDEO_SUFFIX_LIST, fileSuffix.toLowerCase()) &&
                file.getSize() > Constants.FILE_SIZE_MB * sysSettingDto.getMaxFileSize()) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }


        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String fileExtName = StringUtils.getFileSuffix(fileName);
        if (!fileExtName.isEmpty() && !fileExtName.matches("[.][a-zA-Z0-9]{1,16}")) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (cover != null && cover.getSize() > Constants.FILE_SIZE_MB * sysSettingDto.getMaxImageSize()) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String fileRealName = messageId + fileExtName;
        String month = DateUtils.format(new Date(chatMessage.getSendTime()), DateTimePatternEnum.YYYY_MM.getPattern());
        File folder = new File(appConfig.getProjectFolder() + Constants.FILE_FOLDER + month);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File uploadFile = new File(folder.getPath() + "/" + fileRealName);
        try {
            file.transferTo(uploadFile);
            if (cover != null) {
                cover.transferTo(new File(uploadFile.getPath() + Constants.COVER_IMAGE_SUFFIX));
            }
        } catch (Exception e) {
            log.error("上传文件失败，", e);
            throw new BusinessException("文件上传失败");
        }
        chatMessage.setStatus(MessageStatusEnum.SENT.getStatus());
        chatMessage.setFileName(fileName);
        chatMessage.setFileSize(file.getSize());
        chatMessageMapper.updateByMessageId(chatMessage, messageId);

        MessageSendDTO<?> messageSendDTO = new MessageSendDTO<>();
        messageSendDTO.setStatus(MessageStatusEnum.SENT.getStatus());
        messageSendDTO.setMessageId(messageId);
        messageSendDTO.setMessageType(MessageTypeEnum.FILE_UPLOAD.getType());
        messageSendDTO.setContactId(chatMessage.getContactId());
        messageHandler.sendMessage(messageSendDTO);
    }

    /**
     * 文件下载
     */
    @Override
    public File downloadFile(TokenUserInfoDto user, Long messageId, Boolean showCover) {
        if (messageId == null || messageId < 1 || messageId > Integer.MAX_VALUE) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        ChatMessage message = chatMessageMapper.selectByMessageId(messageId.intValue());
        if (message == null || message.getSendTime() == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_602);
        }
        UserContactTypeEnum type = UserContactTypeEnum.getByPrefix(message.getContactId());
        if (type == UserContactTypeEnum.USER) {
            if (!user.getUserId().equals(message.getSendUserId())
                    && !user.getUserId().equals(message.getContactId())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        } else if (type == UserContactTypeEnum.GROUP) {
            UserContactQuery query = new UserContactQuery();
            query.setUserId(user.getUserId());
            query.setContactId(message.getContactId());
            query.setContactType(UserContactTypeEnum.GROUP.getType());
            query.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            Integer count = userContactMapper.selectCount(query);
            if (count == null || count == 0) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        } else {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String suffix = StringUtils.getFileSuffix(message.getFileName());
        if (!suffix.isEmpty() && !suffix.matches("[.][a-zA-Z0-9]{1,16}")) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String month = DateUtils.format(new Date(message.getSendTime()), DateTimePatternEnum.YYYY_MM.getPattern());
        String name = messageId + suffix + (Boolean.TRUE.equals(showCover) ? Constants.COVER_IMAGE_SUFFIX : "");
        File file = new File(appConfig.getProjectFolder() + Constants.FILE_FOLDER + month, name);
        if (!file.isFile()) {
            throw new BusinessException(ResponseCodeEnum.CODE_602);
        }
        return file;
    }
}
