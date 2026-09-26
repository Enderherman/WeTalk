package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.entity.enums.MessageTypeEnum;
import top.enderherman.wetalk.entity.po.*;
import top.enderherman.wetalk.entity.query.*;
import top.enderherman.wetalk.mappers.*;
import top.enderherman.wetalk.service.impl.GroupInfoServiceImpl;
import top.enderherman.wetalk.webSocket.MessageHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroupSessionLifecycleTest {

    private GroupInfoServiceImpl service;
    private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;
    private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;
    private MessageHandler messageHandler;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        service = new GroupInfoServiceImpl();
        groupInfoMapper = mock(GroupInfoMapper.class);
        userContactMapper = mock(UserContactMapper.class);
        chatSessionUserMapper = mock(ChatSessionUserMapper.class);
        chatSessionMapper = mock(ChatSessionMapper.class);
        chatMessageMapper = mock(ChatMessageMapper.class);
        userInfoMapper = mock(UserInfoMapper.class);
        messageHandler = mock(MessageHandler.class);

        ReflectionTestUtils.setField(service, "groupInfoMapper", groupInfoMapper);
        ReflectionTestUtils.setField(service, "userContactMapper", userContactMapper);
        ReflectionTestUtils.setField(service, "chatSessionUserMapper", chatSessionUserMapper);
        ReflectionTestUtils.setField(service, "chatSessionMapper", chatSessionMapper);
        ReflectionTestUtils.setField(service, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(service, "userInfoMapper", userInfoMapper);
        ReflectionTestUtils.setField(service, "messageHandler", messageHandler);
    }

    @Test
    void leavingOrBeingRemovedDropsOnlyThatUsersGroupSession() {
        GroupInfo group = new GroupInfo();
        group.setGroupId("G300");
        group.setGroupOwnId("U100");
        when(groupInfoMapper.selectByGroupId("G300")).thenReturn(group);
        when(userContactMapper.deleteByUserIdAndContactId("U200", "G300")).thenReturn(1);
        when(userContactMapper.selectCount(any(UserContactQuery.class))).thenReturn(1);
        UserInfo user = new UserInfo();
        user.setNickName("Member");
        when(userInfoMapper.selectByUserId("U200")).thenReturn(user);

        service.leaveGroup("U200", "G300", MessageTypeEnum.LEAVE_GROUP);

        verify(chatSessionUserMapper).deleteByUserIdAndContactId("U200", "G300");
    }

    @Test
    void dissolvingGroupDropsEveryActiveGroupSession() {
        GroupInfo group = new GroupInfo();
        group.setGroupId("G300");
        group.setGroupOwnId("U100");
        when(groupInfoMapper.selectByGroupId("G300")).thenReturn(group);
        when(userContactMapper.selectList(any(UserContactQuery.class))).thenReturn(java.util.List.of());

        service.dissolutionGroup("U100", "G300");

        var query = org.mockito.ArgumentCaptor.forClass(ChatSessionUserQuery.class);
        verify(chatSessionUserMapper).deleteByParam(query.capture());
        assertEquals("G300", query.getValue().getContactId());
    }
}
