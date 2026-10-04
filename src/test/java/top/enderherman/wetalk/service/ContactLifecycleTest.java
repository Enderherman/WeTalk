package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.*;
import top.enderherman.wetalk.entity.query.*;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.*;
import top.enderherman.wetalk.service.impl.UserContactServiceImpl;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContactLifecycleTest {
    private UserContactServiceImpl service;
    private UserContactMapper<UserContact, UserContactQuery> contacts;
    private UserInfoMapper<UserInfo, UserInfoQuery> users;
    private GroupInfoMapper<GroupInfo, GroupInfoQuery> groups;
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> sessions;
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> messages;
    private MessageHandler notifications;
    private final Map<String, UserContact> relationships = new HashMap<>();
    private final Map<String, ChatSessionUser> sessionRows = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new UserContactServiceImpl();
        contacts = mock(UserContactMapper.class);
        users = mock(UserInfoMapper.class);
        groups = mock(GroupInfoMapper.class);
        sessions = mock(ChatSessionUserMapper.class);
        messages = mock(ChatMessageMapper.class);
        notifications = mock(MessageHandler.class);
        ReflectionTestUtils.setField(service, "userContactMapper", contacts);
        ReflectionTestUtils.setField(service, "userInfoMapper", users);
        ReflectionTestUtils.setField(service, "groupInfoMapper", groups);
        ReflectionTestUtils.setField(service, "chatSessionUserMapper", sessions);
        ReflectionTestUtils.setField(service, "chatSessionMapper", mock(ChatSessionMapper.class));
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "redisComponent", mock(RedisComponent.class));
        ReflectionTestUtils.setField(service, "channelContextUtils", mock(ChannelContextUtils.class));
        ReflectionTestUtils.setField(service, "messageHandler", notifications);
        when(users.selectByUserId(anyString())).thenAnswer(call -> user(call.getArgument(0)));
        when(contacts.selectByUserIdAndContactId(anyString(), anyString())).thenAnswer(
                call -> relationships.get(call.getArgument(0) + ":" + call.getArgument(1)));
        when(contacts.updateByUserIdAndContactId(any(), anyString(), anyString())).thenAnswer(call -> {
            UserContact row = relationships.get(call.getArgument(1) + ":" + call.getArgument(2));
            if (row == null) return 0;
            row.setStatus(((UserContact) call.getArgument(0)).getStatus());
            return 1;
        });
        when(contacts.insertBatch(anyList())).thenAnswer(call -> {
            for (UserContact row : (List<UserContact>) call.getArgument(0)) {
                if (relationships.containsKey(row.getUserId() + ":" + row.getContactId())) throw new DuplicateKeyException("existing relationship");
                relationships.put(row.getUserId() + ":" + row.getContactId(), row);
            }
            return 2;
        });
        when(contacts.insertOrUpdateBatch(anyList())).thenAnswer(call -> {
            for (UserContact row : (List<UserContact>) call.getArgument(0)) relationships.put(row.getUserId() + ":" + row.getContactId(), row);
            return 2;
        });
        when(sessions.insertBatch(anyList())).thenAnswer(call -> {
            for (ChatSessionUser row : (List<ChatSessionUser>) call.getArgument(0)) {
                if (sessionRows.containsKey(row.getUserId())) throw new DuplicateKeyException("existing session");
                sessionRows.put(row.getUserId(), row);
            }
            return 2;
        });
        when(sessions.insertOrUpdateBatch(anyList())).thenAnswer(call -> {
            for (ChatSessionUser row : (List<ChatSessionUser>) call.getArgument(0)) {
                ChatSessionUser previous = sessionRows.get(row.getUserId());
                if (previous != null) row.setLastReadMessageId(previous.getLastReadMessageId());
                sessionRows.put(row.getUserId(), row);
            }
            return 2;
        });
    }

    private UserInfo user(String id) {
        UserInfo result = new UserInfo();
        result.setUserId(id);
        result.setNickName(id);
        result.setStatus(1);
        result.setIsDelete(0);
        result.setJoinType(0);
        return result;
    }

    private void relation(String user, String contact, int status) {
        UserContact row = new UserContact();
        row.setUserId(user);
        row.setContactId(contact);
        row.setStatus(status);
        relationships.put(user + ":" + contact, row);
    }

    @Test
    void deletedFriendCanBeAddedAgainWithExistingHistoryAndReadCursor() {
        relation("U100", "U200", 2);
        relation("U200", "U100", 3);
        ChatSessionUser old = new ChatSessionUser();
        old.setUserId("U100");
        old.setLastReadMessageId(42);
        sessionRows.put("U100", old);

        service.addContact("U100", "U200", "U200", 0, "again");

        assertEquals(1, relationships.get("U100:U200").getStatus());
        assertEquals(1, relationships.get("U200:U100").getStatus());
        assertEquals(42, sessionRows.get("U100").getLastReadMessageId());
        verify(messages).insert(any());
        verify(notifications, times(2)).sendMessage(any());
    }

    @Test
    void repeatedAdditionDoesNotCreateAnotherWelcomeMessage() {
        relation("U100", "U200", 1);
        relation("U200", "U100", 1);
        service.addContact("U100", "U200", "U200", 0, "again");
        verifyNoInteractions(messages, notifications, sessions);
    }

    @Test
    void deletingBlockedContactPreservesTheirBlockAndRejectsAnewApplication() {
        relation("U100", "U200", 5);
        relation("U200", "U100", 4);
        service.changeContactType("U100", "U200", 2);
        assertEquals(4, relationships.get("U200:U100").getStatus());
        TokenUserInfoDto actor = TokenUserInfoDto.builder().userId("U100").nickName("A").build();
        assertThrows(BusinessException.class, () -> service.apply(actor, "U200", "again"));
        assertThrows(BusinessException.class, () -> service.addContact("U100", "U200", "U200", 0, "again"));
        verifyNoInteractions(messages, notifications);
    }

    @Test
    void friendshipActionsCannotDeleteOrBlockGroupMembership() {
        relation("U100", "G300", 1);
        assertThrows(BusinessException.class, () -> service.changeContactType("U100", "G300", 2));
        assertThrows(BusinessException.class, () -> service.changeContactType("U100", "G300", 4));
        assertEquals(1, relationships.get("U100:G300").getStatus());
    }

    @Test
    void approvalCannotAddMemberToDissolvedGroup() {
        GroupInfo dissolved = new GroupInfo();
        dissolved.setStatus(0);
        when(groups.selectByGroupId("G300")).thenReturn(dissolved);
        assertThrows(BusinessException.class, () -> service.addContact("U100", "U200", "G300", 1, "join"));
        assertTrue(relationships.isEmpty());
        verifyNoInteractions(messages, notifications);
    }

    @Test
    void disabledUserCannotReceiveDirectFriendApplication() {
        UserInfo disabled = user("U200");
        disabled.setStatus(0);
        when(users.selectByUserId("U200")).thenReturn(disabled);
        TokenUserInfoDto actor = TokenUserInfoDto.builder().userId("U100").nickName("A").build();
        assertThrows(BusinessException.class, () -> service.apply(actor, "U200", "hello"));
        assertTrue(relationships.isEmpty());
    }
}
