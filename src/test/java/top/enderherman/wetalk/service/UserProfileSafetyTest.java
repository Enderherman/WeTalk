package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.query.UserInfoQuery;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.UserInfoMapper;
import top.enderherman.wetalk.service.impl.UserInfoServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserProfileSafetyTest {
    private UserInfoServiceImpl service;
    private UserInfoMapper<UserInfo, UserInfoQuery> users;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new UserInfoServiceImpl();
        users = mock(UserInfoMapper.class);
        ReflectionTestUtils.setField(service, "userInfoMapper", users);
        UserInfo existing = new UserInfo();
        existing.setUserId("U100");
        existing.setNickName("Student");
        when(users.selectByUserId("U100")).thenReturn(existing);
    }

    private UserInfo update() {
        UserInfo result = new UserInfo();
        result.setUserId("U100");
        result.setNickName("Student");
        return result;
    }

    @Test
    void profileSubmissionCannotChangeDeletionPasswordEmailOrStatus() throws Exception {
        UserInfo form = update();
        form.setEmail("attacker@example.com");
        form.setPassword("replacement");
        form.setStatus(0);
        form.setIsDelete(1);
        form.setLastOffTime(1L);
        form.setPersonalSignature("Hello");
        form.setJoinType(1);
        service.updateUserInfo(form, null, null);
        ArgumentCaptor<UserInfo> saved = ArgumentCaptor.forClass(UserInfo.class);
        verify(users).updateByUserId(saved.capture(), eq("U100"));
        assertNull(saved.getValue().getIsDelete());
        assertNull(saved.getValue().getPassword());
        assertNull(saved.getValue().getEmail());
        assertNull(saved.getValue().getStatus());
        assertNull(saved.getValue().getLastOffTime());
        assertEquals("Hello", saved.getValue().getPersonalSignature());
        assertEquals(1, saved.getValue().getJoinType());
    }

    @Test
    void invalidProfileFieldsFailBeforeUpdate() {
        UserInfo form = update();
        form.setNickName(" ");
        assertThrows(BusinessException.class, () -> service.updateUserInfo(form, null, null));
        form.setNickName("Student");
        form.setJoinType(10);
        assertThrows(BusinessException.class, () -> service.updateUserInfo(form, null, null));
        form.setJoinType(1);
        form.setPersonalSignature("x".repeat(65));
        assertThrows(BusinessException.class, () -> service.updateUserInfo(form, null, null));
        verify(users, never()).updateByUserId(any(), anyString());
    }

    @Test
    void omittedEditableFieldsRemainOmittedInPartialUpdate() throws Exception {
        UserInfo form = new UserInfo();
        form.setUserId("U100");
        form.setSex(1);
        service.updateUserInfo(form, null, null);
        ArgumentCaptor<UserInfo> saved = ArgumentCaptor.forClass(UserInfo.class);
        verify(users).updateByUserId(saved.capture(), eq("U100"));
        assertNull(saved.getValue().getNickName());
        assertNull(saved.getValue().getPersonalSignature());
        assertEquals(1, saved.getValue().getSex());
    }
}
