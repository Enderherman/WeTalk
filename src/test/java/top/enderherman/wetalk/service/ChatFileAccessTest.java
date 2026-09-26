package top.enderherman.wetalk.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.query.*;
import top.enderherman.wetalk.entity.enums.DateTimePatternEnum;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.*;
import top.enderherman.wetalk.service.impl.ChatMessageServiceImpl;
import top.enderherman.wetalk.utils.DateUtils;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatFileAccessTest {
 @TempDir Path data;
 ChatMessageServiceImpl service;
 ChatMessageMapper messages;
 UserContactMapper contacts;
 RedisComponent redis;
 ChatMessage message;
 @BeforeEach void setup() throws Exception {
  service=new ChatMessageServiceImpl(); messages=mock(ChatMessageMapper.class);
  contacts=mock(UserContactMapper.class); redis=mock(RedisComponent.class);
  AppConfig config=new AppConfig(); config.setProjectFolder(data.toString());
  ReflectionTestUtils.setField(service,"chatMessageMapper",messages);
  ReflectionTestUtils.setField(service,"userContactMapper",contacts);
  ReflectionTestUtils.setField(service,"redisComponent",redis);
  ReflectionTestUtils.setField(service,"appConfig",config);
  message=new ChatMessage(); message.setMessageId(12); message.setSendUserId("Usender");
  message.setContactId("Ureceiver"); message.setFileName("attachment.txt"); message.setSendTime(1700000000000L);
  when(messages.selectByMessageId(12)).thenReturn(message);
  Path file=data.resolve(Constants.FILE_FOLDER).resolve(DateUtils.format(new Date(message.getSendTime()),DateTimePatternEnum.YYYY_MM.getPattern())).resolve("12.txt");
  Files.createDirectories(file.getParent()); Files.writeString(file,"private");
 }
 TokenUserInfoDto user(String id) {return TokenUserInfoDto.builder().userId(id).build();}
 @ParameterizedTest @ValueSource(strings={"Usender","Ureceiver"})
 void participantsCanReadTheirAttachment(String id) {assertEquals("12.txt",service.downloadFile(user(id),12L,false).getName());}
 @Test void aFriendOfTheRecipientCannotReadThirdPartyPrivateMessages() {
  when(redis.getUserContactList("Uthird")).thenReturn(List.of("Ureceiver"));
  assertThrows(BusinessException.class,()->service.downloadFile(user("Uthird"),12L,false));
 }
 @ParameterizedTest @ValueSource(longs={0,-1,2147483648L})
 void invalidIdsAreRejectedBeforeIntegerConversion(long id) {
  assertThrows(BusinessException.class,()->service.downloadFile(user("Ureceiver"),id,false));
 }
 @Test void missingMessageIsAControlledError() {assertThrows(BusinessException.class,()->service.downloadFile(user("Ureceiver"),99L,false));}
 @Test void removedGroupMemberCannotDownload() {
  message.setContactId("Ggroup"); when(contacts.selectCount(any())).thenReturn(0);
  assertThrows(BusinessException.class,()->service.downloadFile(user("Uthird"),12L,false));
 }
 @Test void currentGroupMemberCanDownload() {
  message.setContactId("Ggroup"); when(contacts.selectCount(any())).thenReturn(1);
  assertEquals("12.txt",service.downloadFile(user("Umember"),12L,false).getName());
 }
 @Test void unsafeExtensionCannotEscapeStorage() {
  message.setFileName("x.txt/../../private");
  assertThrows(BusinessException.class,()->service.downloadFile(user("Ureceiver"),12L,false));
 }
}
