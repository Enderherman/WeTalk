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
import top.enderherman.wetalk.mappers.UserContactMapper;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.entity.vo.UserInfoVO;
import top.enderherman.wetalk.entity.enums.UserStatusEnum;
import top.enderherman.wetalk.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AccountSecurityTest {
 UserInfoServiceImpl service;RedisComponent redis;UserInfoMapper users;UserContactMapper<UserContact, UserContactQuery> contacts;MessageHandler handler;
 @BeforeEach void setup() {
  service=new UserInfoServiceImpl();redis=mock(RedisComponent.class);users=mock(UserInfoMapper.class);contacts=mock(UserContactMapper.class);handler=mock(MessageHandler.class);
  ReflectionTestUtils.setField(service,"redisComponent",redis);ReflectionTestUtils.setField(service,"userInfoMapper",users);ReflectionTestUtils.setField(service,"messageHandler",handler);
  ReflectionTestUtils.setField(service,"userContactMapper",contacts);ReflectionTestUtils.setField(service,"appConfig",new AppConfig());
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
 @Test void loginAllowsASecondDeviceWhileTheAccountAlreadyHasAHeartbeat() {
  UserInfo user=new UserInfo();user.setUserId("Uuser");user.setEmail("student@example.test");user.setPassword("compatible-digest");
  user.setNickName("Student");user.setStatus(UserStatusEnum.ENABLE.getStatus());
  when(users.selectByEmail(user.getEmail())).thenReturn(user);
  when(contacts.selectList(any())).thenReturn(java.util.List.of());
  when(redis.getUserHeartBeat(user.getUserId())).thenReturn(System.currentTimeMillis());

  UserInfoVO result=service.login(user.getEmail(),"compatible-digest");

  assertNotNull(result.getToken());verify(redis).saveTokenUserInfoDto(argThat(dto->dto.getUserId().equals("Uuser")));
 }
}
