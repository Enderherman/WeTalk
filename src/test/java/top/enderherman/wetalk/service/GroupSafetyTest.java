package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.*;
import top.enderherman.wetalk.entity.query.*;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.*;
import top.enderherman.wetalk.service.impl.GroupInfoServiceImpl;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupSafetyTest {
    @TempDir Path data;
    private GroupInfoServiceImpl service;
    private GroupInfoMapper<GroupInfo, GroupInfoQuery> groups;
    private UserContactMapper<UserContact, UserContactQuery> contacts;
    private UserInfoMapper<UserInfo, UserInfoQuery> users;
    private UserContactService membership;
    private MessageHandler notifications;
    private SysSettingDto settings;
    private GroupInfo group;
    private final TokenUserInfoDto owner = TokenUserInfoDto.builder().userId("U100").build();
    private final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1};

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new GroupInfoServiceImpl();
        groups = mock(GroupInfoMapper.class);
        contacts = mock(UserContactMapper.class);
        users = mock(UserInfoMapper.class);
        membership = mock(UserContactService.class);
        notifications = mock(MessageHandler.class);
        RedisComponent redis = mock(RedisComponent.class);
        settings = new SysSettingDto();
        settings.setMaxGroupCount(1);
        settings.setMaxGroupMemberCount(3);
        when(redis.getSysSetting()).thenReturn(settings);
        AppConfig config = new AppConfig();
        config.setProjectFolder(data.toString());
        ReflectionTestUtils.setField(service, "groupInfoMapper", groups);
        ReflectionTestUtils.setField(service, "userContactMapper", contacts);
        ReflectionTestUtils.setField(service, "userInfoMapper", users);
        ReflectionTestUtils.setField(service, "userContactService", membership);
        ReflectionTestUtils.setField(service, "messageHandler", notifications);
        ReflectionTestUtils.setField(service, "redisComponent", redis);
        ReflectionTestUtils.setField(service, "appConfig", config);
        ReflectionTestUtils.setField(service, "chatSessionMapper", mock(ChatSessionMapper.class));
        ReflectionTestUtils.setField(service, "chatSessionUserMapper", mock(ChatSessionUserMapper.class));
        ReflectionTestUtils.setField(service, "chatMessageMapper", mock(ChatMessageMapper.class));
        ReflectionTestUtils.setField(service, "channelContextUtils", mock(ChannelContextUtils.class));
        group = new GroupInfo();
        group.setGroupId("G300");
        group.setGroupOwnId("U100");
        group.setGroupName("Group");
        group.setStatus(1);
        group.setJoinType(1);
        when(groups.selectByGroupId("G300")).thenReturn(group);
        when(contacts.selectCount(any())).thenReturn(1);
    }

    private void friend(String id) {
        UserInfo user = new UserInfo();
        user.setUserId(id);
        user.setStatus(1);
        user.setIsDelete(0);
        when(users.selectByUserId(id)).thenReturn(user);
        UserContact relationship = new UserContact();
        relationship.setStatus(1);
        when(contacts.selectByUserIdAndContactId("U100", id)).thenReturn(relationship);
        when(contacts.selectByUserIdAndContactId(id, "U100")).thenReturn(relationship);
    }

    @Test
    void invitationsRequireActiveMutualFriendsAndValidateWholeBatchBeforeChanges() {
        friend("U200");
        assertThrows(BusinessException.class, () -> service.addOrRemoveGroupUser(owner, "G300", "U200,U999", 1));
        verifyNoInteractions(membership, notifications);
    }

    @Test
    void invitationRejectsBlockedReverseRelationship() {
        friend("U200");
        UserContact blocked = new UserContact();
        blocked.setStatus(4);
        when(contacts.selectByUserIdAndContactId("U200", "U100")).thenReturn(blocked);
        assertThrows(BusinessException.class, () -> service.addOrRemoveGroupUser(owner, "G300", "U200", 1));
        verifyNoInteractions(membership);
    }

    @Test
    void duplicateInvitationIdsAreAddedOnce() {
        friend("U200");
        service.addOrRemoveGroupUser(owner, "G300", "U200, U200", 1);
        verify(membership).addContact(eq("U200"), isNull(), eq("G300"), eq(1), anyString());
        verifyNoMoreInteractions(membership);
    }

    @Test
    void rejectsBatchThatExceedsMemberQuotaBeforeAddingAnyMember() {
        friend("U200");
        friend("U201");
        settings.setMaxGroupMemberCount(2);
        assertThrows(BusinessException.class, () -> service.addOrRemoveGroupUser(owner, "G300", "U200,U201", 1));
        verifyNoInteractions(membership);
    }

    @Test
    void invalidOperationAndDissolvedGroupCannotAddMembers() {
        friend("U200");
        assertThrows(BusinessException.class, () -> service.addOrRemoveGroupUser(owner, "G300", "U200", 2));
        group.setStatus(0);
        assertThrows(BusinessException.class, () -> service.addOrRemoveGroupUser(owner, "G300", "U200", 1));
        verifyNoInteractions(membership);
    }

    @Test
    void forgedImageIsRejectedBeforeDatabaseOrBroadcastChanges() {
        MockMultipartFile invalid = new MockMultipartFile("avatarFile", "avatar.png", "image/png", new byte[]{1, 2, 3});
        assertThrows(BusinessException.class, () -> service.saveGroup(group, invalid, null));
        verify(groups, never()).updateByGroupId(any(), anyString());
        verifyNoInteractions(notifications);
    }

    @Test
    void oversizedCoverCannotOverwriteValidAvatar() throws Exception {
        Path avatarPath = data.resolve("file/avatar/G300.png");
        Files.createDirectories(avatarPath.getParent());
        Files.write(avatarPath, png);
        MockMultipartFile avatar = new MockMultipartFile("avatarFile", "avatar.png", "image/png", png);
        MockMultipartFile cover = new MockMultipartFile("coverFile", "cover.png", "image/png", new byte[10 * 1024 * 1024 + 1]);
        assertThrows(BusinessException.class, () -> service.saveGroup(group, avatar, cover));
        assertArrayEquals(png, Files.readAllBytes(avatarPath));
        verify(groups, never()).updateByGroupId(any(), anyString());
    }

    @Test
    void validCoverCanBeUpdatedWithoutReplacingAvatar() throws Exception {
        Path avatarPath = data.resolve("file/avatar/G300.png");
        Files.createDirectories(avatarPath.getParent());
        Files.write(avatarPath, png);
        service.saveGroup(group, null, new MockMultipartFile("coverFile", "cover.png", "image/png", png));
        assertArrayEquals(png, Files.readAllBytes(avatarPath));
        assertArrayEquals(png, Files.readAllBytes(Path.of(avatarPath + "_cover.png")));
    }

    @Test
    void dissolvedGroupsDoNotConsumeNewGroupQuota() {
        // Simulate one dissolved row: an unfiltered count sees it; active-only count does not.
        when(groups.selectCount(any())).thenAnswer(call -> {
            GroupInfoQuery query = call.getArgument(0);
            return Integer.valueOf(1).equals(query.getStatus()) ? 0 : 1;
        });
        group.setGroupId(null);
        service.saveGroup(group, new MockMultipartFile("avatarFile", "avatar.png", "image/png", png), null);
        verify(groups).insert(group);
    }

    @Test
    void blankNameAndInvalidJoinTypeNeverReachDatabase() {
        group.setGroupName(" ");
        assertThrows(BusinessException.class, () -> service.saveGroup(group, null, null));
        group.setGroupName("Group");
        group.setJoinType(99);
        assertThrows(BusinessException.class, () -> service.saveGroup(group, null, null));
        verify(groups, never()).updateByGroupId(any(), anyString());
    }
}
