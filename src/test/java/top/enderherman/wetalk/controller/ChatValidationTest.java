package top.enderherman.wetalk.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.utils.RedisUtils;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.service.ChatMessageService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatValidationTest {
    @Test void avatarIdentifiersCannotTraverseDirectories() {
        ChatController controller = new ChatController();
        RedisUtils redis = mock(RedisUtils.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("token")).thenReturn("test-token");
        when(redis.get(anyString())).thenReturn(TokenUserInfoDto.builder().userId("Uuser").build());
        ReflectionTestUtils.setField(controller, "redisUtils", redis);
        assertThrows(top.enderherman.wetalk.exception.BusinessException.class,
                () -> controller.downloadFile(request, new org.springframework.mock.web.MockHttpServletResponse(),
                        "../../private", false));
    }

    @Test void oversizedTextIsRejectedByTheActualSpringValidationProxy() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ProxyFactory proxy = new ProxyFactory(new ChatController());
            proxy.addAdvice(new MethodValidationInterceptor(factory.getValidator()));
            ChatController controller = (ChatController) proxy.getProxy();
            assertThrows(ConstraintViolationException.class,
                    () -> controller.sendMessage(null, "Upeer", "x".repeat(501), 2, null, null, null));
        }
    }

    @Test void ordinaryFileUploadsDoNotRequireAnImageCover() {
        ChatController target = new ChatController();
        RedisUtils redis = mock(RedisUtils.class);
        ChatMessageService service = mock(ChatMessageService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("token")).thenReturn("test-token");
        when(redis.get(anyString())).thenReturn(TokenUserInfoDto.builder().userId("Uuser").build());
        ReflectionTestUtils.setField(target, "redisUtils", redis);
        ReflectionTestUtils.setField(target, "chatMessageService", service);
        MultipartFile file = mock(MultipartFile.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ProxyFactory proxy = new ProxyFactory(target);
            proxy.addAdvice(new MethodValidationInterceptor(factory.getValidator()));
            ChatController controller = (ChatController) proxy.getProxy();
            assertDoesNotThrow(() -> controller.uploadFile(request, 1, file, null));
            verify(service).saveMessageFile("Uuser", 1, file, null);
        }
    }
}
