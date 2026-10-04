package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.ChatMessageMapper;
import top.enderherman.wetalk.mappers.UserContactMapper;
import top.enderherman.wetalk.service.impl.ChatMessageServiceImpl;
import top.enderherman.wetalk.webSocket.MessageHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageFileUploadSafetyTest {
    @TempDir Path data;
    ChatMessageServiceImpl service;
    ChatMessageMapper<ChatMessage, ChatMessageQuery> messages;
    UserContactMapper<UserContact, UserContactQuery> contacts;
    MessageHandler handler;
    ChatMessage message;
    SysSettingDto settings;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ChatMessageServiceImpl();
        messages = mock(ChatMessageMapper.class);
        contacts = mock(UserContactMapper.class);
        handler = mock(MessageHandler.class);
        RedisComponent redis = mock(RedisComponent.class);
        AppConfig config = new AppConfig();
        config.setProjectFolder(data.toString());
        settings = new SysSettingDto();
        settings.setMaxImageSize(1);
        settings.setMaxVideoSize(1);
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "userContactMapper", contacts);
        ReflectionTestUtils.setField(service, "messageHandler", handler);
        ReflectionTestUtils.setField(service, "redisComponent", redis);
        ReflectionTestUtils.setField(service, "appConfig", config);
        when(redis.getSysSetting()).thenReturn(settings);
        UserContact friend = new UserContact();
        friend.setStatus(1);
        when(contacts.selectByUserIdAndContactId(anyString(), anyString())).thenReturn(friend);
        message = new ChatMessage();
        message.setMessageId(12);
        message.setMessageType(5);
        message.setFileType(2);
        message.setStatus(0);
        message.setSendUserId("Usender");
        message.setContactId("Ureceiver");
        message.setSendTime(1791083519000L);
        message.setFileName("attachment.txt");
        when(messages.selectByMessageIdForUpdate(12)).thenReturn(message);
    }

    private MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("file", name, "application/octet-stream", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Path storedFile(String suffix) {
        return data.resolve(Constants.FILE_FOLDER).resolve("202610").resolve("12" + suffix);
    }

    @Test
    void invalidAttachmentMetadataCannotCreateAPendingMessage() {
        TokenUserInfoDto sender = TokenUserInfoDto.builder().userId("Usender").build();
        assertThrows(BusinessException.class, () -> service.saveMessage(message, sender));
        message.setFileSize(1L); message.setFileType(3);
        assertThrows(BusinessException.class, () -> service.saveMessage(message, sender));
        message.setFileType(2); message.setFileName("../escape.txt");
        assertThrows(BusinessException.class, () -> service.saveMessage(message, sender));
        verify(messages, never()).insert(any());
    }

    @Test
    void rejectsUploadsToTextMessagesAndFromAnotherSender() {
        message.setMessageType(2);
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, file("attachment.txt", "data"), null));
        message.setMessageType(5);
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Uother", 12, file("attachment.txt", "data"), null));
        verifyNoInteractions(handler);
        verify(messages, never()).updateByMessageId(any(), anyInt());
    }

    @Test
    void rejectsMissingEmptyOrUnknownTypeUploads() {
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, null, null));
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, file("empty.txt", ""), null));
        message.setFileType(3);
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, file("a.txt", "data"), null));
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", -1, file("a.txt", "data"), null));
    }

    @ParameterizedTest
    @ValueSource(strings={"../escape.txt", "a\\escape.txt", "line\nname.txt", ".", ".."})
    void rejectsPathAndControlCharactersInNames(String name) {
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, file(name, "data"), null));
        verify(messages, never()).updateByMessageId(any(), anyInt());
    }

    @Test
    void rejectsUploadsAfterFriendshipOrGroupMembershipEndsEvenWithAnOwnedPlaceholder() {
        when(contacts.selectByUserIdAndContactId("Ureceiver", "Usender")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, file("a.txt", "data"), null));
        message.setContactId("Ggroup");
        when(contacts.selectByUserIdAndContactId("Usender", "Ggroup")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, file("a.txt", "data"), null));
    }

    @Test
    void declaredImageAndMediaQuotasCannotBeBypassedByRenamingTheUpload() {
        MockMultipartFile large = new MockMultipartFile("file", "renamed.txt", "application/octet-stream", new byte[]{1}) {
            @Override public long getSize() { return 2 * 1024 * 1024; }
        };
        message.setFileType(0);
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, large, null));
        message.setFileType(1);
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, large, null));
        verify(messages, never()).updateByMessageId(any(), anyInt());
    }

    @Test
    void imageMessageRequiresMatchingImageSignatureAndMimeType() throws Exception {
        message.setFileType(0);
        MockMultipartFile fake = new MockMultipartFile("file", "a.png", "image/png", "not a png".getBytes());
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, fake, null));
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+cWZsAAAAASUVORK5CYII=");
        service.saveMessageFile("Usender", 12, new MockMultipartFile("file", "a.png", "image/png", png), null);
        assertArrayEquals(png, Files.readAllBytes(storedFile(".png")));
    }

    @Test
    void publishesOnlyAfterCommitAndRepeatingIdenticalUploadDoesNotPublishAgain() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.saveMessageFile("Usender", 12, file("attachment.txt", "original"), null);
            verifyNoInteractions(handler);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        assertEquals("original", Files.readString(storedFile(".txt")));
        service.saveMessageFile("Usender", 12, file("attachment.txt", "original"), null);
        verify(handler, times(1)).sendMessage(any());
        verify(messages, times(1)).updateByMessageId(any(), eq(12));
        try (var files = Files.list(storedFile(".txt").getParent())) { assertEquals(1, files.count()); }
    }

    @Test
    void cannotReplacePublishedAttachmentBytesOrFilename() throws Exception {
        service.saveMessageFile("Usender", 12, file("attachment.txt", "original"), null);
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, file("attachment.txt", "changed"), null));
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, file("renamed.txt", "original"), null));
        assertEquals("original", Files.readString(storedFile(".txt")));
        verify(messages, times(1)).updateByMessageId(any(), eq(12));
    }

    @Test
    void failedTransferLeavesMessagePendingAndCleansStagingFile() throws Exception {
        MockMultipartFile interrupted = new MockMultipartFile("file", "attachment.txt", "text/plain", new byte[]{1}) {
            @Override public void transferTo(Path target) throws IOException {
                Files.writeString(target, "partial");
                throw new IOException("interrupted");
            }
        };
        assertThrows(BusinessException.class, () -> service.saveMessageFile("Usender", 12, interrupted, null));
        assertEquals(0, message.getStatus());
        assertFalse(Files.exists(storedFile(".txt")));
        verify(messages, never()).updateByMessageId(any(), anyInt());
        verifyNoInteractions(handler);
        try (var files = Files.list(storedFile(".txt").getParent())) { assertEquals(0, files.count()); }
    }
}
