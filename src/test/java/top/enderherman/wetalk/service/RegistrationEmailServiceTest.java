package top.enderherman.wetalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.utils.RedisUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private RedisUtils<Object> redisUtils;

    private RegistrationEmailService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationEmailService(mailSender, redisUtils, true, "sender@example.com");
    }

    @Test
    void sendsSixDigitCodeAndStoresItForTenMinutes() {
        when(redisUtils.setEx(anyString(), any(), anyLong())).thenReturn(true);
        service.sendRegistrationCode("  User@Example.com ");

        ArgumentCaptor<Object> codeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisUtils).setEx(
                eq(Constants.REDIS_KEY_REGISTER_EMAIL_CODE + "user@example.com"),
                codeCaptor.capture(),
                eq(Constants.REDIS_KEY_EXPIRES_TEN_MIN.longValue()));
        String code = (String) codeCaptor.getValue();
        assertThat(code).matches("[0-9]{6}");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getTo()).containsExactly("user@example.com");
        assertThat(messageCaptor.getValue().getText()).contains(code).contains("10 分钟");
    }

    @Test
    void rejectsIncorrectOrExpiredCodes() {
        when(redisUtils.get(Constants.REDIS_KEY_REGISTER_EMAIL_CODE + "user@example.com"))
                .thenReturn("123456");

        assertThatThrownBy(() -> service.verifyRegistrationCode("user@example.com", "654321"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("邮箱验证码错误或已过期");
    }

    @Test
    void consumesTheCodeAfterSuccessfulRegistration() {
        service.consumeRegistrationCode("User@Example.com");

        verify(redisUtils).delete(Constants.REDIS_KEY_REGISTER_EMAIL_CODE + "user@example.com");
    }

    @Test
    void removesCodeWhenMailDeliveryFails() {
        when(redisUtils.setEx(anyString(), any(), anyLong())).thenReturn(true);
        doThrow(new MailSendException("mail transport failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.sendRegistrationCode("user@example.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码发送失败，请稍后重试");
        verify(redisUtils).delete(Constants.REDIS_KEY_REGISTER_EMAIL_CODE + "user@example.com");
    }

    @Test
    void refusesToSendWhenEmailIsNotConfigured() {
        RegistrationEmailService disabled = new RegistrationEmailService(
                mailSender, redisUtils, false, "sender@example.com");

        assertThatThrownBy(() -> disabled.sendRegistrationCode("user@example.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("邮箱验证码服务暂不可用");
        verifyNoInteractions(mailSender);
    }
}
