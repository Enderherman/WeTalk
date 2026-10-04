package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.entity.query.UserInfoQuery;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.UserContactMapper;
import top.enderherman.wetalk.mappers.UserInfoMapper;
import top.enderherman.wetalk.service.impl.UserContactServiceImpl;
import top.enderherman.wetalk.webSocket.MessageHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContactRemarkTest {
    private UserContactServiceImpl service;
    private UserContactMapper<UserContact, UserContactQuery> contacts;
    private UserInfoMapper<UserInfo, UserInfoQuery> users;
    private UserInfo friend;
    private final Map<String, UserContact> rows = new HashMap<>();
    private final List<MessageSendDTO<?>> events = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new UserContactServiceImpl();
        contacts = mock(UserContactMapper.class);
        users = mock(UserInfoMapper.class);
        MessageHandler handler = mock(MessageHandler.class);
        ReflectionTestUtils.setField(service, "userContactMapper", contacts);
        ReflectionTestUtils.setField(service, "userInfoMapper", users);
        ReflectionTestUtils.setField(service, "messageHandler", handler);
        relationship("U100", "U200", "A private label");
        relationship("U200", "U100", "B private label");
        friend = new UserInfo();
        friend.setUserId("U200");
        friend.setNickName("Real Name");
        friend.setStatus(1);
        friend.setIsDelete(0);
        when(users.selectByUserId("U200")).thenReturn(friend);
        when(users.selectPublicContactByUserId("U200")).thenReturn(friend);
        when(contacts.selectByUserIdAndContactId(anyString(), anyString())).thenAnswer(call -> rows.get(call.getArgument(0) + ":" + call.getArgument(1)));
        when(contacts.updateRemark(anyString(), anyString(), nullable(String.class))).thenAnswer(call -> {
            UserContact row = rows.get(call.getArgument(0) + ":" + call.getArgument(1));
            row.setRemark(call.getArgument(2));
            return 1;
        });
        doAnswer(call -> { events.add(call.getArgument(0)); return null; }).when(handler).sendMessage(any());
    }

    private void relationship(String userId, String contactId, String remark) {
        UserContact row = new UserContact();
        row.setUserId(userId);
        row.setContactId(contactId);
        row.setContactType(0);
        row.setStatus(1);
        row.setRemark(remark);
        rows.put(userId + ":" + contactId, row);
    }

    @Test
    void saveTrimsAndChangesOnlyTheCallersPrivateRelationship() {
        assertEquals(Map.of("contactId", "U200", "remark", "New label"), service.saveRemark("U100", "U200", "  New label  "));
        assertEquals("New label", rows.get("U100:U200").getRemark());
        assertEquals("B private label", rows.get("U200:U100").getRemark());
        assertEquals("Real Name", friend.getNickName());
        assertEquals(18, events.get(0).getMessageType());
        assertEquals("U100", events.get(0).getContactId());
        assertNull(events.get(0).getSendUserNickName());
        assertEquals(Map.of("contactId", "U200", "remark", "New label"), events.get(0).getExtentData());
        verify(users, never()).updateByUserId(any(), anyString());
    }

    @Test
    void blankRemarkClearsOnlyCurrentAccountsLabel() {
        assertEquals("", service.saveRemark("U100", "U200", "   ").get("remark"));
        assertNull(rows.get("U100:U200").getRemark());
        assertEquals("B private label", rows.get("U200:U100").getRemark());
        assertEquals("", ((Map<?, ?>) events.get(0).getExtentData()).get("remark"));
    }

    @Test
    void rejectsGroupsSelfStrangersAndOversizedRemarksWithoutWriting() {
        assertThrows(BusinessException.class, () -> service.saveRemark("U100", "G300", "group"));
        assertThrows(BusinessException.class, () -> service.saveRemark("U100", "U100", "self"));
        assertThrows(BusinessException.class, () -> service.saveRemark("U999", "U200", "stranger"));
        assertThrows(BusinessException.class, () -> service.saveRemark("U100", "U200", "x".repeat(41)));
        verify(contacts, never()).updateRemark(anyString(), anyString(), nullable(String.class));
        assertTrue(events.isEmpty());
    }

    @Test
    void bothRelationshipDirectionsMustStillBeFriends() {
        for (int status : new int[]{2, 3, 4, 5, 7}) {
            rows.get("U100:U200").setStatus(status);
            assertThrows(BusinessException.class, () -> service.saveRemark("U100", "U200", "changed"));
            rows.get("U100:U200").setStatus(1);
            rows.get("U200:U100").setStatus(status);
            assertThrows(BusinessException.class, () -> service.saveRemark("U100", "U200", "changed"));
            rows.get("U200:U100").setStatus(1);
        }
        verify(contacts, never()).updateRemark(anyString(), anyString(), nullable(String.class));
    }

    @Test
    void lostRelationshipDuringAtomicUpdateDoesNotEmitSuccessfulEvent() {
        doReturn(0).when(contacts).updateRemark(anyString(), anyString(), nullable(String.class));
        assertThrows(BusinessException.class, () -> service.saveRemark("U100", "U200", "changed"));
        assertTrue(events.isEmpty());
    }

    @Test
    void searchDisplaysOwnRemarkAlongsideUnchangedPublicNickname() {
        assertEquals("A private label", service.searchContact("U100", "U200").getRemark());
        assertEquals("Real Name", service.searchContact("U100", "U200").getNickName());
        assertNull(service.searchContact("U999", "U200").getRemark());
    }

    @Test
    void onlyCommittedChangesAreBroadcastToDevices() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.saveRemark("U100", "U200", "committed label");
            assertTrue(events.isEmpty());
            for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit();
            assertEquals(1, events.size());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rolledBackChangeNeverBroadcastsPrivateRemark() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.saveRemark("U100", "U200", "rollback label");
            for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertTrue(events.isEmpty());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
