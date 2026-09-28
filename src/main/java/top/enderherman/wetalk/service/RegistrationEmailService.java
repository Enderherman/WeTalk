package top.enderherman.wetalk.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.utils.RedisUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

@Service
public class RegistrationEmailService {

    private static final int CODE_LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final RedisUtils<Object> redisUtils;
    private final boolean emailEnabled;
    private final String senderAddress;

    public RegistrationEmailService(
            JavaMailSender mailSender,
            RedisUtils<Object> redisUtils,
            @Value("${wetalk.email.enabled:false}") boolean emailEnabled,
            @Value("${spring.mail.username:}") String senderAddress) {
        this.mailSender = mailSender;
        this.redisUtils = redisUtils;
        this.emailEnabled = emailEnabled;
        this.senderAddress = senderAddress;
    }

    public void ensureConfigured() {
        if (!emailEnabled || senderAddress == null || senderAddress.isBlank()) {
            throw new BusinessException("邮箱验证码服务暂不可用");
        }
    }

    public void sendRegistrationCode(String email) {
        ensureConfigured();
        String normalizedEmail = normalizeEmail(email);
        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        String redisKey = registrationCodeKey(normalizedEmail);

        if (!redisUtils.setEx(redisKey, code, Constants.REDIS_KEY_EXPIRES_TEN_MIN)) {
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(normalizedEmail);
        message.setSubject("WeTalk 邮箱验证码");
        message.setText("你的 WeTalk 注册验证码是：" + code + "。验证码 10 分钟内有效；如非本人操作，请忽略此邮件。");
        try {
            mailSender.send(message);
        } catch (MailException error) {
            redisUtils.delete(redisKey);
            throw new BusinessException("验证码发送失败，请稍后重试");
        }
    }

    public void verifyRegistrationCode(String email, String submittedCode) {
        ensureConfigured();
        String normalizedEmail = normalizeEmail(email);
        Object storedValue = redisUtils.get(registrationCodeKey(normalizedEmail));
        if (!(storedValue instanceof String storedCode)
                || submittedCode == null
                || !MessageDigest.isEqual(
                        storedCode.getBytes(StandardCharsets.UTF_8),
                        submittedCode.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("邮箱验证码错误或已过期");
        }
    }

    public void consumeRegistrationCode(String email) {
        redisUtils.delete(registrationCodeKey(normalizeEmail(email)));
    }

    private static String registrationCodeKey(String email) {
        return Constants.REDIS_KEY_REGISTER_EMAIL_CODE + email;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
