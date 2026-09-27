package top.enderherman.wetalk.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.service.ChatMessageService;
import top.enderherman.wetalk.utils.RedisUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaRangeStreamTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private RedisUtils<?> redisUtils;

    @InjectMocks
    private ChatController controller;

    @TempDir
    Path tempDir;

    private TokenUserInfoDto user;
    private ChatMessage message;
    private FileFixture file;

    @BeforeEach
    void prepareFixture() throws Exception {
        user = new TokenUserInfoDto();
        user.setUserId("U100");
        doReturn(user).when(redisUtils).get(Constants.REDIS_KEY_WS_TOKEN + "qa-session");
        message = new ChatMessage();
        message.setMessageId(77);
        message.setFileName("clip.mp4");
        message.setFileType(1);
        message.setStatus(1);
        Path path = tempDir.resolve("clip.mp4");
        Files.writeString(path, "0123456789", StandardCharsets.UTF_8);
        file = new FileFixture(path.toFile());
        when(chatMessageService.downloadFile(user, 77L, false)).thenReturn(file.file());
        when(chatMessageService.getChatMessageByMessageId(77)).thenReturn(message);
    }

    @Test
    void streamsOnlyTheRequestedByteRange() throws Exception {
        MockHttpServletRequest request = requestWithRange("bytes=2-5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.streamMedia(request, response, 77L);

        assertEquals(206, response.getStatus());
        assertEquals("bytes", response.getHeader("Accept-Ranges"));
        assertEquals("bytes 2-5/10", response.getHeader("Content-Range"));
        assertEquals("video/mp4", response.getContentType());
        assertEquals("2345", response.getContentAsString());
    }

    @Test
    void supportsSuffixRangesForResumablePlayback() throws Exception {
        MockHttpServletRequest request = requestWithRange("bytes=-3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.streamMedia(request, response, 77L);

        assertEquals(206, response.getStatus());
        assertEquals("bytes 7-9/10", response.getHeader("Content-Range"));
        assertEquals("789", response.getContentAsString());
    }

    @Test
    void sendsTheWholeAuthorizedMediaFileWhenRangeIsAbsent() throws Exception {
        MockHttpServletRequest request = requestWithRange(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.streamMedia(request, response, 77L);

        assertEquals(200, response.getStatus());
        assertEquals("0123456789", response.getContentAsString());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    void rejectsUnsatisfiableRangesAndNonMediaMessages() throws Exception {
        MockHttpServletRequest request = requestWithRange("bytes=10-");
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.streamMedia(request, response, 77L);
        assertEquals(416, response.getStatus());
        assertEquals("bytes */10", response.getHeader("Content-Range"));

        message.setFileType(2);
        assertThrows(BusinessException.class,
                () -> controller.streamMedia(requestWithRange("bytes=0-1"), new MockHttpServletResponse(), 77L));
    }

    private MockHttpServletRequest requestWithRange(String range) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat/streamMedia");
        request.addHeader("token", "qa-session");
        if (range != null) request.addHeader("Range", range);
        return request;
    }

    private record FileFixture(java.io.File file) {}
}
