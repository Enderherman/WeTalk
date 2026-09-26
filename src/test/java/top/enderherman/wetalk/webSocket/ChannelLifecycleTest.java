package top.enderherman.wetalk.webSocket;
import org.junit.jupiter.api.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.mappers.*;
import java.util.List;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ChannelLifecycleTest {
 ChannelContextUtils context; RedisComponent redis; UserInfoMapper users; ChatMessageMapper messages;
 @BeforeEach void setup() {
  ChannelContextUtils.USER_CONTEXT_MAP.clear(); ChannelContextUtils.GROUP_CONTEXT_MAP.clear();
  context=new ChannelContextUtils();redis=mock(RedisComponent.class);users=mock(UserInfoMapper.class);messages=mock(ChatMessageMapper.class);
  ReflectionTestUtils.setField(context,"redisComponent",redis);ReflectionTestUtils.setField(context,"userInfoMapper",users);
  ReflectionTestUtils.setField(context,"chatMessageMapper",messages);
 }
 @AfterEach void clear() {
  ChannelContextUtils.USER_CONTEXT_MAP.values().forEach(c->c.close());
  ChannelContextUtils.USER_CONTEXT_MAP.clear(); ChannelContextUtils.GROUP_CONTEXT_MAP.clear();
 }
 @Test void unauthenticatedDisconnectDoesNotWriteNullUserToRedisOrDatabase() {
  EmbeddedChannel channel=new EmbeddedChannel(); context.removeContext(channel);channel.finishAndReleaseAll();
  verifyNoInteractions(redis,users);
 }
 @Test void oldConnectionCannotEraseAReplacementConnection() {
  EmbeddedChannel old=new EmbeddedChannel(), current=new EmbeddedChannel();
  old.attr(ChannelContextUtils.USER_ID).set("Uuser");ChannelContextUtils.USER_CONTEXT_MAP.put("Uuser",current);
  context.removeContext(old);assertSame(current,ChannelContextUtils.USER_CONTEXT_MAP.get("Uuser"));verifyNoInteractions(redis,users);
  old.finishAndReleaseAll();
 }
 @Test void authenticatedDisconnectRemovesHeartbeat() {
  EmbeddedChannel channel=new EmbeddedChannel(); channel.attr(ChannelContextUtils.USER_ID).set("Uuser");
  ChannelContextUtils.USER_CONTEXT_MAP.put("Uuser",channel);context.removeContext(channel);
  verify(redis).removeUserHeartBeat("Uuser");verify(users).updateByUserId(any(),eq("Uuser"));
  assertFalse(ChannelContextUtils.USER_CONTEXT_MAP.containsKey("Uuser"));channel.finishAndReleaseAll();
 }
 @Test void dissolvingAGroupDoesNotDisconnectMembersOtherConversations() {
  EmbeddedChannel member=new EmbeddedChannel();DefaultChannelGroup group=new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);group.add(member);
  ChannelContextUtils.GROUP_CONTEXT_MAP.put("Ggroup",group);
  MessageSendDTO dto=new MessageSendDTO();dto.setContactId("Ggroup");dto.setMessageType(8);context.sendMessage(dto);
  assertTrue(member.isOpen());assertFalse(ChannelContextUtils.GROUP_CONTEXT_MAP.containsKey("Ggroup"));member.finishAndReleaseAll();
 }
 @Test void initNotificationPreservesDestinationAndSharedDto() {
  EmbeddedChannel channel=new EmbeddedChannel();ChannelContextUtils.USER_CONTEXT_MAP.put("Uuser",channel);
  MessageSendDTO dto=new MessageSendDTO();dto.setMessageType(0);dto.setContactId("Uuser");context.sendMessage(dto,"Uuser");
  TextWebSocketFrame frame=channel.readOutbound();assertTrue(frame.text().contains("Uuser"));assertEquals("Uuser",dto.getContactId());frame.release();
 }
 @Test void deliveryDoesNotMutatePublishedMessageForOtherListeners() {
  EmbeddedChannel channel=new EmbeddedChannel();ChannelContextUtils.USER_CONTEXT_MAP.put("Ureceiver",channel);
  MessageSendDTO dto=new MessageSendDTO();dto.setMessageType(2);dto.setContactId("Ureceiver");dto.setSendUserId("Usender");context.sendMessage(dto,"Ureceiver");
  assertEquals("Ureceiver",dto.getContactId());TextWebSocketFrame frame=channel.readOutbound();assertTrue(frame.text().contains("Usender"));frame.release();
 }
 @Test void initialSyncUsesRecentTimestampInsteadOfThreeDayDuration() {
  UserInfo user=new UserInfo();user.setUserId("Uuser");user.setLastOffTime(1L);
  when(users.selectByUserId("Uuser")).thenReturn(user);when(redis.getUserContactList("Uuser")).thenReturn(List.of());
  ChatSessionUserMapper sessions=mock(ChatSessionUserMapper.class);when(sessions.selectList(any())).thenReturn(List.of());
  UserContactApplyMapper applications=mock(UserContactApplyMapper.class);when(applications.selectCount(any())).thenReturn(0);
  when(messages.selectList(any())).thenReturn(List.of());
  ReflectionTestUtils.setField(context,"chatSessionUserMapper",sessions);ReflectionTestUtils.setField(context,"userContactApplyMapper",applications);
  long before=System.currentTimeMillis();EmbeddedChannel channel=new EmbeddedChannel();context.addContext("Uuser",channel);
  ArgumentCaptor<ChatMessageQuery> query=ArgumentCaptor.forClass(ChatMessageQuery.class);verify(messages).selectList(query.capture());
  assertTrue(query.getValue().getLastReceiveTime()>=before-Constants.MILLISECONDS_THREE_DAY);
  channel.finishAndReleaseAll();
 }
}
