package top.enderherman.wetalk;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.enderherman.wetalk.utils.RedisUtils;
import top.enderherman.wetalk.webSocket.netty.NettyWebSocketStart;
import javax.sql.DataSource;
import java.sql.Connection;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class StartupTest {
 @Test void startupClosesProbeConnectionAndStartsWebsocket() throws Exception {
  InitRun runner=new InitRun();DataSource source=mock(DataSource.class);Connection connection=mock(Connection.class);
  RedisUtils redis=mock(RedisUtils.class);NettyWebSocketStart ws=mock(NettyWebSocketStart.class);
  when(source.getConnection()).thenReturn(connection);when(connection.isValid(5)).thenReturn(true);
  ReflectionTestUtils.setField(runner,"dataSource",source);ReflectionTestUtils.setField(runner,"redisUtils",redis);ReflectionTestUtils.setField(runner,"nettyWebSocketStart",ws);
  runner.run(null);verify(connection).close();verify(ws,timeout(1000)).run();
 }
 @Test void dependencyFailureStopsStartupInsteadOfReportingReady() throws Exception {
  InitRun runner=new InitRun();DataSource source=mock(DataSource.class);Connection connection=mock(Connection.class);
  RedisUtils redis=mock(RedisUtils.class);NettyWebSocketStart ws=mock(NettyWebSocketStart.class);
  when(source.getConnection()).thenReturn(connection);when(connection.isValid(5)).thenReturn(false);
  ReflectionTestUtils.setField(runner,"dataSource",source);ReflectionTestUtils.setField(runner,"redisUtils",redis);ReflectionTestUtils.setField(runner,"nettyWebSocketStart",ws);
  assertThrows(IllegalStateException.class,()->runner.run(null));verify(connection).close();verifyNoInteractions(ws,redis);
 }
}
