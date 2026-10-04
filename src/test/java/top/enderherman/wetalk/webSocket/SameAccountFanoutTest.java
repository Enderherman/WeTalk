package top.enderherman.wetalk.webSocket;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.po.ChatSessionUser;
import top.enderherman.wetalk.entity.query.ChatSessionUserQuery;
import top.enderherman.wetalk.mappers.ChatSessionUserMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SameAccountFanoutTest {
    private ChannelContextUtils context;
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> sessions;
    private final List<EmbeddedChannel> channels = new ArrayList<>();
    private EmbeddedChannel senderBrowser, senderDesktop, receiverBrowser, receiverDesktop, outsider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ChannelContextUtils.USER_CONTEXT_MAP.clear();
        ChannelContextUtils.GROUP_CONTEXT_MAP.clear();
        context = new ChannelContextUtils();
        sessions = mock(ChatSessionUserMapper.class);
        ReflectionTestUtils.setField(context, "chatSessionUserMapper", sessions);
        ChatSessionUser session = new ChatSessionUser();
        session.setContactName("Receiver public name");
        session.setRemark("Private sender-only remark");
        when(sessions.selectByUserIdAndContactId("Usender", "Ureceiver")).thenReturn(session);
        senderBrowser = channel("Usender", "browser");
        senderDesktop = channel("Usender", "desktop");
        receiverBrowser = channel("Ureceiver", "browser");
        receiverDesktop = channel("Ureceiver", "desktop");
        outsider = channel("Uoutsider", "browser");
    }

    private EmbeddedChannel channel(String userId, String device) {
        EmbeddedChannel channel = new EmbeddedChannel(DefaultChannelId.newInstance());
        channels.add(channel);
        ChannelContextUtils.USER_CONTEXT_MAP.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(device, channel);
        return channel;
    }

    @AfterEach
    void closeChannels() {
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        ChannelContextUtils.USER_CONTEXT_MAP.clear();
        ChannelContextUtils.GROUP_CONTEXT_MAP.values().forEach(group -> group.clear());
        ChannelContextUtils.GROUP_CONTEXT_MAP.clear();
    }

    private MessageSendDTO<?> event(int type) {
        MessageSendDTO<?> event = new MessageSendDTO<>();
        event.setMessageId(42);
        event.setSessionId("shared-session");
        event.setMessageType(type);
        event.setContactId("Ureceiver");
        event.setContactType(0);
        event.setSendUserId("Usender");
        event.setSendUserNickName("Sender real name");
        event.setMessageContent("hello");
        event.setFileName("final-report.txt");
        event.setFileSize(123L);
        event.setFileType(2);
        event.setStatus(1);
        return event;
    }

    private JSONObject read(EmbeddedChannel channel) {
        channel.runPendingTasks();
        TextWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame, "Every active device of the account must receive the event");
        try {
            JSONObject payload = JSONUtil.parseObj(frame.text());
            assertFalse(frame.text().contains("Private sender-only remark"));
            return payload;
        } finally { frame.release(); }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 5, 6})
    void privateChatUpdatesReachBothAccountsDevicesWithTheirOwnContactPerspective(int type) {
        MessageSendDTO<?> original = event(type);
        context.sendMessage(original);
        for (EmbeddedChannel receiver : List.of(receiverBrowser, receiverDesktop)) {
            JSONObject payload = read(receiver);
            assertEquals("Usender", payload.getStr("contactId"));
            assertEquals("Sender real name", payload.getStr("contactName"));
            assertEquals(42, payload.getInt("messageId"));
            assertEquals(type, payload.getInt("messageType"));
            assertNull(receiver.readOutbound());
        }
        for (EmbeddedChannel sender : List.of(senderBrowser, senderDesktop)) {
            JSONObject payload = read(sender);
            assertEquals("Ureceiver", payload.getStr("contactId"));
            assertEquals("Receiver public name", payload.getStr("contactName"));
            assertEquals("Usender", payload.getStr("sendUserId"));
            assertEquals("shared-session", payload.getStr("sessionId"));
            assertEquals(type, payload.getInt("messageType"));
            assertEquals("final-report.txt", payload.getStr("fileName"));
            assertNull(sender.readOutbound());
        }
        assertNull(outsider.readOutbound());
        assertEquals("Ureceiver", original.getContactId());
        assertNull(original.getContactName(), "Do not mutate the DTO shared with other Redis listeners");
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 18})
    void readAndPrivateRemarkControlEventsAreNeverMirroredToTheSender(int type) {
        context.sendMessage(event(type));
        read(receiverBrowser);
        read(receiverDesktop);
        assertNull(senderBrowser.readOutbound());
        assertNull(senderDesktop.readOutbound());
        assertNull(outsider.readOutbound());
        verifyNoInteractions(sessions);
    }

    @Test
    void unknownTargetNameDoesNotTurnSenderEchoIntoASelfConversation() {
        when(sessions.selectByUserIdAndContactId("Usender", "Ureceiver")).thenReturn(null);
        context.sendMessage(event(2));
        JSONObject payload = read(senderBrowser);
        assertEquals("Ureceiver", payload.getStr("contactId"));
        assertNull(payload.getStr("contactName"));
    }

    @Test
    void selfAddressedMessageIsDeliveredOnlyOncePerDevice() {
        MessageSendDTO<?> message = event(2);
        message.setContactId("Usender");
        context.sendMessage(message);
        read(senderBrowser);
        read(senderDesktop);
        assertNull(senderBrowser.readOutbound());
        assertNull(senderDesktop.readOutbound());
        assertNull(receiverBrowser.readOutbound());
    }

    @Test
    void groupFanoutAlreadyIncludesSendersAndDoesNotCreateExtraCopies() {
        DefaultChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        group.addAll(List.of(senderBrowser, senderDesktop, receiverBrowser, receiverDesktop));
        ChannelContextUtils.GROUP_CONTEXT_MAP.put("Ggroup", group);
        MessageSendDTO<?> event = event(5);
        event.setContactId("Ggroup");
        event.setContactType(1);
        context.sendMessage(event);
        for (EmbeddedChannel channel : List.of(senderBrowser, senderDesktop, receiverBrowser, receiverDesktop)) {
            assertEquals("Ggroup", read(channel).getStr("contactId"));
            assertNull(channel.readOutbound());
        }
        assertNull(outsider.readOutbound());
    }
}
