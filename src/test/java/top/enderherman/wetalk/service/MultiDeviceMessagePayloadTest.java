package top.enderherman.wetalk.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.DefaultChannelId;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.*;
import top.enderherman.wetalk.entity.query.*;
import top.enderherman.wetalk.mappers.*;
import top.enderherman.wetalk.service.impl.ChatMessageServiceImpl;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultiDeviceMessagePayloadTest {
    @TempDir Path files;
    private ChatMessageServiceImpl service;
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> messages;
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> sessions;
    private AiService ai;
    private final List<EmbeddedChannel> channels = new ArrayList<>();
    private EmbeddedChannel senderBrowser, senderDesktop, receiver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ChannelContextUtils.USER_CONTEXT_MAP.clear();
        service = new ChatMessageServiceImpl();
        messages = mock(ChatMessageMapper.class);
        sessions = mock(ChatSessionUserMapper.class);
        ai = mock(AiService.class);
        RedisComponent redis = mock(RedisComponent.class);
        UserContactMapper<UserContact, UserContactQuery> contacts = mock(UserContactMapper.class);
        MessageHandler handler = mock(MessageHandler.class);
        ChannelContextUtils context = new ChannelContextUtils();
        ReflectionTestUtils.setField(context, "chatSessionUserMapper", sessions);
        doAnswer(call -> { context.sendMessage(call.getArgument(0)); return null; }).when(handler).sendMessage(any());
        ReflectionTestUtils.setField(service, "messageHandler", handler);
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "chatSessionMapper", mock(ChatSessionMapper.class));
        ReflectionTestUtils.setField(service, "redisComponent", redis);
        ReflectionTestUtils.setField(service, "userContactMapper", contacts);
        ReflectionTestUtils.setField(service, "aiService", ai);
        AppConfig config = new AppConfig();
        config.setProjectFolder(files.toString());
        ReflectionTestUtils.setField(service, "appConfig", config);
        SysSettingDto settings = new SysSettingDto();
        settings.setRobotNickName("Configured Robot");
        when(redis.getSysSetting()).thenReturn(settings);
        when(redis.getUserContactList("Usender")).thenReturn(List.of("Ureceiver", "Urobot"));
        UserContact friend = new UserContact();
        friend.setStatus(1);
        when(contacts.selectByUserIdAndContactId(anyString(), anyString())).thenReturn(friend);
        senderBrowser = channel("Usender", "browser");
        senderDesktop = channel("Usender", "desktop");
        receiver = channel("Ureceiver", "browser");
    }

    private EmbeddedChannel channel(String userId, String device) {
        EmbeddedChannel channel = new EmbeddedChannel(DefaultChannelId.newInstance());
        channels.add(channel);
        ChannelContextUtils.USER_CONTEXT_MAP.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(device, channel);
        return channel;
    }

    @AfterEach
    void cleanChannels() {
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        ChannelContextUtils.USER_CONTEXT_MAP.clear();
    }

    private List<JSONObject> drain(EmbeddedChannel channel) {
        List<JSONObject> values = new ArrayList<>();
        TextWebSocketFrame frame;
        while ((frame = channel.readOutbound()) != null) {
            try { values.add(JSONUtil.parseObj(frame.text())); }
            finally { frame.release(); }
        }
        return values;
    }

    @Test
    void aiPromptReachesBothOwnDevicesBeforeReplyWithRobotConversationAndSameHttpMessageId() {
        when(ai.isEnabled()).thenReturn(true);
        when(ai.sendMsgFlow(anyString())).thenReturn(Flux.just("AI answer"));
        AtomicInteger ids = new AtomicInteger(40);
        when(messages.insert(any())).thenAnswer(call -> { ((ChatMessage) call.getArgument(0)).setMessageId(ids.incrementAndGet()); return 1; });
        ChatSessionUser oldSession = new ChatSessionUser();
        oldSession.setContactName("Outdated Robot");
        when(sessions.selectByUserIdAndContactId("Usender", "Urobot")).thenReturn(oldSession);
        ChatMessage request = new ChatMessage();
        request.setContactId("Urobot");
        request.setMessageType(2);
        request.setMessageContent("Question from browser");
        request.setClientMessageId("00000000-0000-4000-8000-000000000041");
        MessageSendDTO<?> http = service.saveMessage(request, TokenUserInfoDto.builder().userId("Usender").nickName("Sender real name").build());
        for (EmbeddedChannel channel : List.of(senderBrowser, senderDesktop)) {
            List<JSONObject> events = drain(channel);
            assertEquals(List.of(2, 14, 15, 16), events.stream().map(event -> event.getInt("messageType")).toList());
            JSONObject prompt = events.get(0);
            assertEquals(http.getMessageId(), prompt.getInt("messageId"));
            assertEquals(http.getClientMessageId(), prompt.getStr("clientMessageId"));
            assertEquals("Urobot", prompt.getStr("contactId"));
            assertEquals("Configured Robot", prompt.getStr("contactName"));
            assertEquals("Question from browser", prompt.getStr("messageContent"));
            assertEquals("Usender", prompt.getStr("sendUserId"));
        }
        assertNull(receiver.readOutbound());
    }

    @Test
    void completionCarriesFinalAttachmentMetadataToBothViewsOnlyAfterCommit() {
        ChatMessage message = new ChatMessage();
        message.setMessageId(42);
        message.setSessionId("attachment-session");
        message.setMessageType(5);
        message.setContactType(0);
        message.setContactId("Ureceiver");
        message.setSendUserId("Usender");
        message.setSendUserNickName("Sender real name");
        message.setSendTime(1791083519000L);
        message.setMessageContent("[文件]");
        message.setFileType(2);
        message.setFileName("old-placeholder.txt");
        message.setFileSize(999L);
        message.setStatus(0);
        when(messages.selectByMessageIdForUpdate(42)).thenReturn(message);
        byte[] actual = "uploaded bytes".getBytes(StandardCharsets.UTF_8);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.saveMessageFile("Usender", 42, new MockMultipartFile("file", "最终文件.txt", "text/plain", actual), null);
            assertNull(senderBrowser.readOutbound());
            assertNull(senderDesktop.readOutbound());
            assertNull(receiver.readOutbound());
            TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCommit());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        for (EmbeddedChannel channel : List.of(receiver, senderBrowser, senderDesktop)) {
            List<JSONObject> events = drain(channel);
            assertEquals(1, events.size());
            JSONObject event = events.get(0);
            assertEquals(6, event.getInt("messageType"));
            assertEquals(42, event.getInt("messageId"));
            assertEquals("attachment-session", event.getStr("sessionId"));
            assertEquals(channel == receiver ? "Usender" : "Ureceiver", event.getStr("contactId"));
            assertEquals("Usender", event.getStr("sendUserId"));
            assertEquals("Sender real name", event.getStr("sendUserNickName"));
            assertEquals(1791083519000L, event.getLong("sendTime"));
            assertEquals(1, event.getInt("status"));
            assertEquals("最终文件.txt", event.getStr("fileName"));
            assertEquals((long) actual.length, event.getLong("fileSize"));
            assertEquals(2, event.getInt("fileType"));
            assertEquals(0, event.getInt("contactType"));
            assertFalse(event.toString().contains(files.toString()));
        }
        assertEquals(5, message.getMessageType(), "The persisted history must remain a media message");
    }
}
