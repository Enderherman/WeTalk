package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.entity.enums.UserContactTypeEnum;
import top.enderherman.wetalk.entity.enums.UserStatusEnum;
import top.enderherman.wetalk.entity.po.GroupInfo;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.query.GroupInfoQuery;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.entity.query.UserInfoQuery;
import top.enderherman.wetalk.entity.vo.UserContactSearchResultVO;
import top.enderherman.wetalk.mappers.GroupInfoMapper;
import top.enderherman.wetalk.mappers.UserContactMapper;
import top.enderherman.wetalk.mappers.UserInfoMapper;
import top.enderherman.wetalk.service.impl.UserContactServiceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactKeywordSearchTest {

    @Mock
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Mock
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Mock
    private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;

    private UserContactServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserContactServiceImpl();
        ReflectionTestUtils.setField(service, "userContactMapper", userContactMapper);
        ReflectionTestUtils.setField(service, "userInfoMapper", userInfoMapper);
        ReflectionTestUtils.setField(service, "groupInfoMapper", groupInfoMapper);
    }

    @Test
    void findsTheExactUserByEmailWithoutReturningTheEmailAddress() {
        UserInfo user = new UserInfo();
        user.setUserId("U200");
        user.setEmail("alice@example.com");
        user.setNickName("Alice");
        user.setStatus(UserStatusEnum.ENABLE.getStatus());
        user.setIsDelete(0);
        when(userInfoMapper.selectPublicContactByEmail("alice@example.com")).thenReturn(user);
        when(userContactMapper.selectByUserIdAndContactId("U100", "U200")).thenReturn(null);

        List<UserContactSearchResultVO> results = service.searchContactsByKeyword("U100", " alice@example.com ");

        assertEquals(1, results.size());
        assertEquals("U200", results.get(0).getContactId());
        assertEquals("Alice", results.get(0).getNickName());
        assertEquals(UserContactTypeEnum.USER.toString(), results.get(0).getContactType());
        assertNull(results.get(0).getStatus());
        verify(userInfoMapper, never()).selectPublicContactByUserId(anyString());
        verify(userInfoMapper, never()).selectByEmail(anyString());
        verify(groupInfoMapper, never()).selectActiveGroupsByNameFuzzy(anyString(), anyInt(), anyInt());
    }

    @Test
    void returnsFuzzyUserAndActiveGroupMatchesWithBoundedQueries() {
        UserInfo user = new UserInfo();
        user.setUserId("U201");
        user.setNickName("Alice Example");
        GroupInfo group = new GroupInfo();
        group.setGroupId("G301");
        group.setGroupName("Alice Study Group");
        when(userInfoMapper.selectActiveContactsByNicknameFuzzy(anyString(), anyInt(), anyInt())).thenReturn(List.of(user));
        when(groupInfoMapper.selectActiveGroupsByNameFuzzy(anyString(), anyInt(), anyInt())).thenReturn(List.of(group));
        when(userContactMapper.selectByUserIdAndContactId(anyString(), anyString())).thenReturn(null);

        List<UserContactSearchResultVO> results = service.searchContactsByKeyword("U100", "Alice");

        assertEquals(2, results.size());
        assertEquals("U201", results.get(0).getContactId());
        assertEquals("USER", results.get(0).getContactType());
        assertEquals("G301", results.get(1).getContactId());
        assertEquals("GROUP", results.get(1).getContactType());

        verify(userInfoMapper).selectActiveContactsByNicknameFuzzy("Alice", 0, 10);
        verify(groupInfoMapper).selectActiveGroupsByNameFuzzy("Alice", 0, 10);
    }

    @Test
    void treatsLikeWildcardsAsLiteralNicknameText() {
        service.searchContactsByKeyword("U100", "%_=");

        verify(userInfoMapper).selectActiveContactsByNicknameFuzzy("=%=_==", 0, 10);
        verify(groupInfoMapper).selectActiveGroupsByNameFuzzy("=%=_==", 0, 10);
    }

    @Test
    void normalizesThePrefixForExactContactIds() {
        UserInfo user = new UserInfo();
        user.setUserId("U204");
        user.setNickName("Case Insensitive ID");
        user.setStatus(UserStatusEnum.ENABLE.getStatus());
        user.setIsDelete(0);
        when(userInfoMapper.selectPublicContactByUserId("U204")).thenReturn(user);
        when(userContactMapper.selectByUserIdAndContactId("U100", "U204")).thenReturn(null);

        List<UserContactSearchResultVO> results = service.searchContactsByKeyword("U100", "u204");

        assertEquals(1, results.size());
        assertEquals("U204", results.get(0).getContactId());
        verify(userInfoMapper).selectPublicContactByUserId("U204");
    }

    @Test
    void doesNotExposeDisabledOrDeletedUsersThroughEmailSearch() {
        UserInfo disabledUser = new UserInfo();
        disabledUser.setUserId("U202");
        disabledUser.setStatus(UserStatusEnum.DISABLE.getStatus());
        disabledUser.setIsDelete(0);
        UserInfo deletedUser = new UserInfo();
        deletedUser.setUserId("U203");
        deletedUser.setStatus(UserStatusEnum.ENABLE.getStatus());
        deletedUser.setIsDelete(1);
        when(userInfoMapper.selectPublicContactByEmail("disabled@example.com")).thenReturn(disabledUser);
        when(userInfoMapper.selectPublicContactByEmail("deleted@example.com")).thenReturn(deletedUser);

        assertEquals(0, service.searchContactsByKeyword("U100", "disabled@example.com").size());
        assertEquals(0, service.searchContactsByKeyword("U100", "deleted@example.com").size());
        verify(userContactMapper, never()).selectByUserIdAndContactId(anyString(), anyString());
    }
}
