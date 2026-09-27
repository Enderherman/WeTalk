package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.po.ChatSession;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.entity.query.ChatSessionQuery;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.ChatMessageMapper;
import top.enderherman.wetalk.mappers.ChatSessionMapper;
import top.enderherman.wetalk.service.impl.ChatMessageServiceImpl;
import top.enderherman.wetalk.service.AiService;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageIdempotencyTest {
    private static final String CLIENT_MESSAGE_ID = "a1b2c3d4-1234-4abc-8def-1234567890ab";

    private ChatMessageServiceImpl service;
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> messages;
    private ChatSessionMapper<ChatSession, ChatSessionQuery> sessions;
    private MessageHandler messageHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ChatMessageServiceImpl();
        messages = mock(ChatMessageMapper.class);
        sessions = mock(ChatSessionMapper.class);
        messageHandler = mock(MessageHandler.class);
        RedisComponent redis = mock(RedisComponent.class);
        when(redis.getUserContactList("U100")).thenReturn(List.of("U200"));
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "chatSessionMapper", sessions);
        ReflectionTestUtils.setField(service, "messageHandler", messageHandler);
        ReflectionTestUtils.setField(service, "redisComponent", redis);
        ReflectionTestUtils.setField(service, "aiService", mock(AiService.class));
    }

    @Test
    void returnsTheExistingMessageForAnExactRetryWithoutRepeatingSideEffects() {
        ChatMessage existing = existingMessage("Hello");
        when(messages.selectBySendUserIdAndClientMessageId("U100", CLIENT_MESSAGE_ID)).thenReturn(existing);

        MessageSendDTO<?> result = service.saveMessage(request("Hello"), user());

        assertEquals(42, result.getMessageId());
        assertEquals(CLIENT_MESSAGE_ID, result.getClientMessageId());
        verify(messages, never()).insert(any(ChatMessage.class));
        verify(sessions, never()).updateBySessionId(any(ChatSession.class), anyString());
        verify(messageHandler, never()).sendMessage(any(MessageSendDTO.class));
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithDifferentText() {
        when(messages.selectBySendUserIdAndClientMessageId("U100", CLIENT_MESSAGE_ID))
                .thenReturn(existingMessage("Original text"));

        assertThrows(BusinessException.class, () -> service.saveMessage(request("Changed text"), user()));
        verify(messages, never()).insert(any(ChatMessage.class));
        verify(sessions, never()).updateBySessionId(any(ChatSession.class), anyString());
    }

    @Test
    void resolvesConcurrentDuplicateInsertByReadingTheWinningMessage() {
        ChatMessage existing = existingMessage("Hello");
        when(messages.selectBySendUserIdAndClientMessageId("U100", CLIENT_MESSAGE_ID))
                .thenReturn(null, existing);
        when(messages.insert(any(ChatMessage.class))).thenThrow(new DuplicateKeyException("duplicate client key"));

        MessageSendDTO<?> result = service.saveMessage(request("Hello"), user());

        assertEquals(42, result.getMessageId());
        verify(sessions, never()).updateBySessionId(any(ChatSession.class), anyString());
        verify(messageHandler, never()).sendMessage(any(MessageSendDTO.class));
    }

    private static TokenUserInfoDto user() {
        return TokenUserInfoDto.builder().userId("U100").nickName("Student").build();
    }

    private static ChatMessage request(String content) {
        ChatMessage message = new ChatMessage();
        message.setContactId("U200");
        message.setMessageContent(content);
        message.setMessageType(2);
        message.setClientMessageId(CLIENT_MESSAGE_ID);
        return message;
    }

    private static ChatMessage existingMessage(String content) {
        ChatMessage message = request(content);
        message.setMessageId(42);
        message.setSessionId(StringUtils.getChatSessionId4User(new String[] { "U100", "U200" }));
        message.setSendUserId("U100");
        message.setSendUserNickName("Student");
        message.setMessageContent(content);
        message.setSendTime(1_000L);
        message.setContactType(0);
        message.setStatus(1);
        return message;
    }
}
