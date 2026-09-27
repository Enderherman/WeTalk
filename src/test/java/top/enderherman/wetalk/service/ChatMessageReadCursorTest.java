package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.po.ChatSessionUser;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.entity.query.ChatSessionUserQuery;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.ChatMessageMapper;
import top.enderherman.wetalk.mappers.ChatSessionUserMapper;
import top.enderherman.wetalk.service.impl.ChatMessageServiceImpl;
import top.enderherman.wetalk.utils.StringUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ChatMessageServiceImpl();
        sessions = mock(ChatSessionUserMapper.class);
        messages = mock(ChatMessageMapper.class);
        redis = mock(RedisComponent.class);
        ReflectionTestUtils.setField(service, "chatSessionUserMapper", sessions);
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "redisComponent", redis);

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
    }

    @Test
    void advancesTheAuthenticatedUsersReadCursorForAMemberMessage() {
        service.markRead(TokenUserInfoDto.builder().userId("U100").build(), "U200", 42);

        verify(sessions).updateLastReadMessageId("U100", SESSION_ID, 42);
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
