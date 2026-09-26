package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.enums.UserContactTypeEnum;
import top.enderherman.wetalk.entity.po.*;
import top.enderherman.wetalk.entity.query.*;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.*;
import top.enderherman.wetalk.service.impl.UserContactServiceImpl;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroupMemberQuotaTest {

    private UserContactServiceImpl service;
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;
    private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;
    private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;
    private RedisComponent redisComponent;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        service = new UserContactServiceImpl();
        userContactMapper = mock(UserContactMapper.class);
        userInfoMapper = mock(UserInfoMapper.class);
        groupInfoMapper = mock(GroupInfoMapper.class);
        chatSessionMapper = mock(ChatSessionMapper.class);
        chatSessionUserMapper = mock(ChatSessionUserMapper.class);
        chatMessageMapper = mock(ChatMessageMapper.class);
        redisComponent = mock(RedisComponent.class);

        ReflectionTestUtils.setField(service, "userContactMapper", userContactMapper);
        ReflectionTestUtils.setField(service, "userInfoMapper", userInfoMapper);
        ReflectionTestUtils.setField(service, "groupInfoMapper", groupInfoMapper);
        ReflectionTestUtils.setField(service, "chatSessionMapper", chatSessionMapper);
        ReflectionTestUtils.setField(service, "chatSessionUserMapper", chatSessionUserMapper);
        ReflectionTestUtils.setField(service, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(service, "redisComponent", redisComponent);
        ReflectionTestUtils.setField(service, "channelContextUtils", mock(ChannelContextUtils.class));
        ReflectionTestUtils.setField(service, "messageHandler", mock(MessageHandler.class));
    }

    @Test
    void usesMemberQuotaInsteadOfPerUserGroupCreationQuota() {
        SysSettingDto settings = new SysSettingDto();
        settings.setMaxGroupCount(1);
        settings.setMaxGroupMemberCount(3);
        when(redisComponent.getSysSetting()).thenReturn(settings);
        when(userContactMapper.selectCount(any(UserContactQuery.class))).thenReturn(2, 3);

        GroupInfo group = new GroupInfo();
        group.setGroupId("G300");
        group.setGroupName("Study Group");
        when(groupInfoMapper.selectByGroupId("G300")).thenReturn(group);
        UserInfo member = new UserInfo();
        member.setNickName("Member");
        when(userInfoMapper.selectByUserId("U200")).thenReturn(member);

        service.addContact("U200", null, "G300", UserContactTypeEnum.GROUP.getType(), "joined the group");

        verify(userContactMapper).insertBatch(any());
        verify(chatSessionUserMapper).insertOrUpdate(any(ChatSessionUser.class));
    }

    @Test
    void rejectsAnAdditionWhenTheConfiguredMemberQuotaIsReached() {
        SysSettingDto settings = new SysSettingDto();
        settings.setMaxGroupCount(10);
        settings.setMaxGroupMemberCount(2);
        when(redisComponent.getSysSetting()).thenReturn(settings);
        when(userContactMapper.selectCount(any(UserContactQuery.class))).thenReturn(2);

        assertThrows(BusinessException.class, () -> service.addContact(
                "U200", null, "G300", UserContactTypeEnum.GROUP.getType(), "joined the group"));

        verify(userContactMapper, never()).insertBatch(any());
    }
}
