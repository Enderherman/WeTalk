package top.enderherman.wetalk.service;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.service.impl.UserInfoServiceImpl;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.webSocket.MessageHandler;
import top.enderherman.wetalk.mappers.UserInfoMapper;
import top.enderherman.wetalk.entity.enums.UserStatusEnum;
import top.enderherman.wetalk.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AccountSecurityTest {
 UserInfoServiceImpl service;RedisComponent redis;UserInfoMapper users;MessageHandler handler;
 @BeforeEach void setup() {
  service=new UserInfoServiceImpl();redis=mock(RedisComponent.class);users=mock(UserInfoMapper.class);handler=mock(MessageHandler.class);
  ReflectionTestUtils.setField(service,"redisComponent",redis);ReflectionTestUtils.setField(service,"userInfoMapper",users);ReflectionTestUtils.setField(service,"messageHandler",handler);
 }
 @Test void forcedLogoutInvalidatesRestCredentialsWithoutAnOpenSocket() {
  service.forcedOffOnline("Uuser");verify(redis).clearTokenUserInfoDto("Uuser");verify(handler).sendMessage(any());
 }
 @Test void disablingAccountRevokesExistingCredentials() {
  service.updateUserStatus("Uuser",UserStatusEnum.DISABLE.getStatus());verify(redis).clearTokenUserInfoDto("Uuser");
 }
 @Test void publicRegistrationCannotClaimAdministratorEmail() {
  AppConfig config=new AppConfig();config.setAdminEmails(" admin@example.test ");
  ReflectionTestUtils.setField(service,"appConfig",config);
  assertThrows(BusinessException.class,()->service.register("ADMIN@example.test","name","password"));verifyNoInteractions(users);
 }
 @Test void apiJsonDoesNotContainPasswordButInputCanStillBeRead() throws Exception {
  UserInfo user=new UserInfo();user.setPassword("private-hash");
  String json=new ObjectMapper().writeValueAsString(user);assertFalse(json.contains("private-hash"));assertFalse(json.contains("password"));
  assertEquals("secret",new ObjectMapper().readValue("{\"password\":\"secret\"}",UserInfo.class).getPassword());
 }
 @Test void onlineStateHandlesNullOfflineTimestamp() {
  UserInfo user=new UserInfo();user.setLastLoginTime(new java.util.Date());assertDoesNotThrow(user::getOnlineType);
 }
}
