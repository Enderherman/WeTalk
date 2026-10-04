package top.enderherman.wetalk.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.vo.UserInfoVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.service.UserContactService;
import top.enderherman.wetalk.service.UserInfoService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContactDetailTest {
    private UserContactController controller(UserInfoService users, UserContactService contacts) {
        UserContactController controller = new UserContactController() {
            @Override protected TokenUserInfoDto getTokenUserDto(HttpServletRequest request) {
                return TokenUserInfoDto.builder().userId("U100").build();
            }
        };
        ReflectionTestUtils.setField(controller, "userInfoService", users);
        ReflectionTestUtils.setField(controller, "userContactService", contacts);
        return controller;
    }

    @Test
    void deletedAndBlockedRelationshipsAreNotReportedAsFriends() {
        UserInfoService users = mock(UserInfoService.class);
        UserContactService contacts = mock(UserContactService.class);
        UserInfo user = new UserInfo();
        user.setUserId("U200");
        when(users.getUserInfoByUserId("U200")).thenReturn(user);
        UserContact relationship = new UserContact();
        when(contacts.getUserContactByUserIdAndContactId("U100", "U200")).thenReturn(relationship);
        UserContactController controller = controller(users, contacts);
        for (int status : new int[]{2, 3, 4, 5, 7}) {
            relationship.setStatus(status);
            UserInfoVO result = (UserInfoVO) controller.getContactInfo(new MockHttpServletRequest(), "U200").getData();
            assertEquals(status, result.getContactStatus());
        }
    }

    @Test
    void missingUserReturnsBusinessErrorInsteadOfNullPointer() {
        UserContactController controller = controller(mock(UserInfoService.class), mock(UserContactService.class));
        assertThrows(BusinessException.class, () -> controller.getContactInfo(new MockHttpServletRequest(), "U999"));
    }

    @Test
    void contactDetailReturnsPrivateRemarkWithoutRenamingTheUser() {
        UserInfoService users = mock(UserInfoService.class);
        UserContactService contacts = mock(UserContactService.class);
        UserInfo user = new UserInfo();
        user.setUserId("U200");
        user.setNickName("Real Name");
        when(users.getUserInfoByUserId("U200")).thenReturn(user);
        UserContact relationship = new UserContact();
        relationship.setStatus(1);
        relationship.setRemark("Private Label");
        when(contacts.getUserContactByUserIdAndContactId("U100", "U200")).thenReturn(relationship);
        UserContactController controller = controller(users, contacts);
        UserInfoVO publicDetail = (UserInfoVO) controller.getContactInfo(new MockHttpServletRequest(), "U200").getData();
        UserInfoVO friendDetail = (UserInfoVO) controller.getContactUserInfo(new MockHttpServletRequest(), "U200").getData();
        assertEquals("Private Label", publicDetail.getRemark());
        assertEquals("Private Label", friendDetail.getRemark());
        assertEquals("Real Name", friendDetail.getNickName());
    }

    @Test
    void remarkUpdateAlwaysUsesAuthenticatedUserIdentity() {
        UserContactService contacts = mock(UserContactService.class);
        UserContactController controller = controller(mock(UserInfoService.class), contacts);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("userId", "U999");
        controller.saveRemark(request, "U200", "Private Label");
        verify(contacts).saveRemark("U100", "U200", "Private Label");
    }
}
