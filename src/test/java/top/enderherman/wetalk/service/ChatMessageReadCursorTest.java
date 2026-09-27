package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.po.ChatSessionUser;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.entity.query.ChatSessionUserQuery;
import top.enderherman.wetalk.entity.enums.MessageTypeEnum;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.ChatMessageMapper;
import top.enderherman.wetalk.mappers.ChatSessionUserMapper;
import top.enderherman.wetalk.service.impl.ChatMessageServiceImpl;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageReadCursorTest {
    private static final String SESSION_ID = StringUtils.getChatSessionId4User(new String[] { "U100", "U200" });

    private ChatMessageServiceImpl service;
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> sessions;
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> messages;
    private RedisComponent redis;
    private MessageHandler messageHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ChatMessageServiceImpl();
        sessions = mock(ChatSessionUserMapper.class);
        messages = mock(ChatMessageMapper.class);
        redis = mock(RedisComponent.class);
        messageHandler = mock(MessageHandler.class);
        ReflectionTestUtils.setField(service, "chatSessionUserMapper", sessions);
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "redisComponent", redis);
        ReflectionTestUtils.setField(service, "messageHandler", messageHandler);

        when(redis.getUserContactList("U100")).thenReturn(List.of("U200"));
        ChatSessionUser sessionUser = new ChatSessionUser();
        sessionUser.setUserId("U100");
        sessionUser.setContactId("U200");
        sessionUser.setSessionId(SESSION_ID);
        when(sessions.selectByUserIdAndContactId("U100", "U200")).thenReturn(sessionUser);
        ChatMessage message = new ChatMessage();
        message.setMessageId(42);
        message.setSessionId(SESSION_ID);
        when(messages.selectByMessageId(42)).thenReturn(message);
        when(sessions.updateLastReadMessageId(anyString(), anyString(), anyInt())).thenReturn(1);
    }

    @Test
    void advancesTheAuthenticatedUsersReadCursorForAMemberMessage() {
        service.markRead(TokenUserInfoDto.builder().userId("U100").build(), "U200", 42);

        verify(sessions).updateLastReadMessageId("U100", SESSION_ID, 42);
        verify(messageHandler).sendMessage(argThat(receipt ->
                receipt.getMessageType().equals(MessageTypeEnum.READ_RECEIPT.getType())
                        && receipt.getMessageId().equals(42)
                        && receipt.getSessionId().equals(SESSION_ID)
                        && receipt.getContactId().equals("U200")
                        && receipt.getSendUserId().equals("U100")));
    }

    @Test
    void doesNotSendAReceiptWhenTheReadCursorDoesNotAdvance() {
        ChatSessionUser sessionUser = new ChatSessionUser();
        sessionUser.setUserId("U100");
        sessionUser.setContactId("U200");
        sessionUser.setSessionId(SESSION_ID);
        sessionUser.setLastReadMessageId(42);
        when(sessions.selectByUserIdAndContactId("U100", "U200")).thenReturn(sessionUser);

        service.markRead(TokenUserInfoDto.builder().userId("U100").build(), "U200", 42);

        verify(sessions, never()).updateLastReadMessageId(anyString(), anyString(), anyInt());
        verify(messageHandler, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void groupReadCursorsDoNotBroadcastIndividualReceipts() {
        String groupId = "G300";
        String groupSessionId = StringUtils.getChatSessionId4Group(groupId);
        when(redis.getUserContactList("U100")).thenReturn(List.of(groupId));
        ChatSessionUser groupSession = new ChatSessionUser();
        groupSession.setUserId("U100");
        groupSession.setContactId(groupId);
        groupSession.setSessionId(groupSessionId);
        when(sessions.selectByUserIdAndContactId("U100", groupId)).thenReturn(groupSession);
        ChatMessage groupMessage = new ChatMessage();
        groupMessage.setMessageId(43);
        groupMessage.setSessionId(groupSessionId);
        when(messages.selectByMessageId(43)).thenReturn(groupMessage);

        service.markRead(TokenUserInfoDto.builder().userId("U100").build(), groupId, 43);

        verify(sessions).updateLastReadMessageId("U100", groupSessionId, 43);
        verify(messageHandler, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsReadCursorsForContactsTheUserCannotAccess() {
        when(redis.getUserContactList("U100")).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> service.markRead(TokenUserInfoDto.builder().userId("U100").build(), "U200", 42));
        verify(sessions, never()).updateLastReadMessageId(anyString(), anyString(), anyInt());
    }

    @Test
    void rejectsMessageIdsFromAnotherConversation() {
        ChatMessage otherMessage = new ChatMessage();
        otherMessage.setMessageId(43);
        otherMessage.setSessionId("Sother");
        when(messages.selectByMessageId(43)).thenReturn(otherMessage);

        assertThrows(BusinessException.class,
                () -> service.markRead(TokenUserInfoDto.builder().userId("U100").build(), "U200", 43));
        verify(sessions, never()).updateLastReadMessageId(anyString(), anyString(), anyInt());
    }
}
