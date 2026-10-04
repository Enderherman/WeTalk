package top.enderherman.wetalk.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.*;

class MailTimeoutConfigTest {
    private ApplicationContextRunner runner(String protocol) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    try {
                        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                                .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException(error);
                    }
                })
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withPropertyValues("MAIL_PROTOCOL=" + protocol, "spring.mail.host=localhost");
    }

    @Test
    void smtpAndSmtpsBothHaveFiniteConnectReadAndWriteTimeouts() {
        for (String protocol : new String[]{"smtp", "smtps"}) {
            runner(protocol).run(context -> {
                assertNull(context.getStartupFailure());
                JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                assertEquals(protocol, sender.getProtocol());
                var properties = sender.getJavaMailProperties();
                assertEquals("5000", properties.getProperty("mail." + protocol + ".connectiontimeout"));
                assertEquals("10000", properties.getProperty("mail." + protocol + ".timeout"));
                assertEquals("10000", properties.getProperty("mail." + protocol + ".writetimeout"));
                assertEquals("false", properties.getProperty("mail.debug"));
            });
        }
    }

    @Test
    void deploymentMayOverrideTimeoutsThroughDocumentedEnvironmentVariables() {
        runner("smtps").withPropertyValues("MAIL_CONNECTION_TIMEOUT=2500", "MAIL_READ_TIMEOUT=7000", "MAIL_WRITE_TIMEOUT=8000")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var properties = context.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
                    assertEquals("2500", properties.getProperty("mail.smtps.connectiontimeout"));
                    assertEquals("7000", properties.getProperty("mail.smtps.timeout"));
                    assertEquals("8000", properties.getProperty("mail.smtps.writetimeout"));
                });
    }
}
