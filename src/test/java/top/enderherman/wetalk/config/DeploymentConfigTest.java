package top.enderherman.wetalk.config;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.chat.client.ChatClient;
import top.enderherman.wetalk.service.impl.AiServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
class DeploymentConfigTest {
 @Test void redissonUsesSameCredentialsDatabaseAndTlsAsSpringRedis() {
  RedisConfig config=new RedisConfig();
  ReflectionTestUtils.setField(config,"redisHost","redis.example");ReflectionTestUtils.setField(config,"redisPort",6380);
  ReflectionTestUtils.setField(config,"password","test-secret");ReflectionTestUtils.setField(config,"username","test-user");
  ReflectionTestUtils.setField(config,"database",3);ReflectionTestUtils.setField(config,"ssl",true);
  var server=config.buildConfig().useSingleServer();
  assertEquals("rediss://redis.example:6380",server.getAddress());assertEquals(3,server.getDatabase());
  assertEquals("test-secret",server.getPassword());assertEquals("test-user",server.getUsername());
 }
 @Test void disabledAiDoesNotRequireAnApiKeyOrChatClient() {
  new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class,ChatClientAutoConfiguration.class))
    .withUserConfiguration(ChatConfig.class,AiServiceImpl.class)
    .withPropertyValues("spring.ai.model.chat=none","spring.ai.chat.client.enabled=false","wetalk.ai.enabled=false")
    .run(context->{assertNull(context.getStartupFailure());assertEquals(0,context.getBeansOfType(ChatClient.class).size());assertFalse(context.getBean(AiServiceImpl.class).isEnabled());});
 }
 @Test void storageDirectoryWorksWithoutTrailingSlash() {
  AppConfig config=new AppConfig();config.setProjectFolder("./data");
  assertTrue(config.getProjectFolder().endsWith(java.io.File.separator));
 }
}
