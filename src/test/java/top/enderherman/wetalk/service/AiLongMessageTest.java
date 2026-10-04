package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.po.ChatSession;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.entity.query.ChatSessionQuery;
import top.enderherman.wetalk.mappers.ChatMessageMapper;
import top.enderherman.wetalk.mappers.ChatSessionMapper;
import top.enderherman.wetalk.service.impl.ChatMessageServiceImpl;
import top.enderherman.wetalk.webSocket.MessageHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiLongMessageTest {
    private ChatMessageServiceImpl service;
    private AiService ai;
    private final Map<Integer, ChatMessage> persisted = new HashMap<>();
    private final List<MessageSendDTO<?>> events = new ArrayList<>();
    private final List<String> previews = new ArrayList<>();
    private final TokenUserInfoDto user = TokenUserInfoDto.builder().userId("U100").nickName("Student").build();
    private static final String LONG_RESPONSE = "Long answer 😀 ".repeat(100);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ChatMessageServiceImpl();
        ChatMessageMapper<ChatMessage, ChatMessageQuery> messages = mock(ChatMessageMapper.class);
        ChatSessionMapper<ChatSession, ChatSessionQuery> sessions = mock(ChatSessionMapper.class);
        MessageHandler notifications = mock(MessageHandler.class);
        RedisComponent redis = mock(RedisComponent.class);
        ai = mock(AiService.class);
        when(ai.isEnabled()).thenReturn(true);
        when(redis.getUserContactList("U100")).thenReturn(List.of(Constants.ROBOT_UID, "U200"));
        when(redis.getSysSetting()).thenReturn(new SysSettingDto());
        AtomicInteger ids = new AtomicInteger();
        when(messages.insert(any())).thenAnswer(call -> {
            ChatMessage message = call.getArgument(0);
            message.setMessageId(ids.incrementAndGet());
            persisted.put(message.getMessageId(), message);
            return 1;
        });
        when(messages.selectByMessageId(anyInt())).thenAnswer(call -> persisted.get(call.getArgument(0)));
        when(messages.updateByMessageId(any(), anyInt())).thenAnswer(call -> {
            ChatMessage update = call.getArgument(0);
            ChatMessage row = persisted.get(call.getArgument(1));
            row.setMessageContent(update.getMessageContent());
            row.setStatus(update.getStatus());
            return 1;
        });
        when(sessions.updateBySessionId(any(), anyString())).thenAnswer(call -> {
            String preview = ((ChatSession) call.getArgument(0)).getLastMessage();
            if (preview.codePointCount(0, preview.length()) > 500) throw new IllegalStateException("SQL preview column overflow");
            previews.add(preview);
            return 1;
        });
        doAnswer(call -> { events.add(call.getArgument(0)); return null; }).when(notifications).sendMessage(any());
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "chatSessionMapper", sessions);
        ReflectionTestUtils.setField(service, "messageHandler", notifications);
        ReflectionTestUtils.setField(service, "redisComponent", redis);
        ReflectionTestUtils.setField(service, "aiService", ai);
    }

    private void ask() {
        service.saveMessage(request(Constants.ROBOT_UID, "Explain the topic"), user);
    }

    private ChatMessage request(String contact, String text) {
        ChatMessage request = new ChatMessage();
        request.setContactId(contact);
        request.setMessageContent(text);
        request.setMessageType(2);
        return request;
    }

    private void assertFinal(String text, int status) {
        assertEquals(text, persisted.get(2).getMessageContent());
        assertEquals(status, persisted.get(2).getStatus());
        MessageSendDTO<?> last = events.get(events.size() - 1);
        assertEquals(16, last.getMessageType());
        assertEquals(text, last.getMessageContent());
        assertEquals(status, last.getStatus());
        String preview = previews.get(previews.size() - 1);
        assertTrue(preview.codePointCount(0, preview.length()) <= 500);
        assertTrue(preview.endsWith("…"));
    }

    @Test
    void longAiReplyPersistsInFullWhileSessionSummaryFitsItsColumn() {
        when(ai.sendMsgFlow(anyString())).thenReturn(Flux.just(LONG_RESPONSE));
        ask();
        assertFinal(LONG_RESPONSE, 1);
    }

    @Test
    void cancellingLongPartialReplyPreservesFullTextAndFinalStatus() {
        Sinks.Many<String> stream = Sinks.many().unicast().onBackpressureBuffer();
        when(ai.sendMsgFlow(anyString())).thenReturn(stream.asFlux());
        ask();
        stream.tryEmitNext(LONG_RESPONSE);
        service.cancelAiMessage(2, user);
        assertFinal(LONG_RESPONSE, 2);
    }

    @Test
    void providerFailurePreservesLongPartialReply() {
        when(ai.sendMsgFlow(anyString())).thenReturn(Flux.concat(Flux.just(LONG_RESPONSE), Flux.error(new IllegalStateException("provider failed"))));
        ask();
        assertFinal(LONG_RESPONSE, 3);
    }

    @Test
    void maximumLengthPrivateMessageKeepsFullBodyWithoutOverflowingNicknamePrefixedPreview() {
        String body = "x".repeat(500);
        service.saveMessage(request("U200", body), user);
        assertEquals(body, persisted.get(1).getMessageContent());
        assertEquals(500, previews.get(0).length());
        assertTrue(previews.get(0).endsWith("…"));
    }
}
