package top.enderherman.wetalk.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.utils.RedisUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component("redisComponent")
public class RedisComponent {

    @Resource
    private RedisUtils redisUtils;

    /**
     * 获取系统参数
     */
    public SysSettingDto getSysSetting() {
        SysSettingDto sysSettingDto = (SysSettingDto) redisUtils.get(Constants.REDIS_KEY_SYS_SETTING);
        sysSettingDto = sysSettingDto == null ? new SysSettingDto() : sysSettingDto;
        return sysSettingDto;
    }

    /**
     * 保存系统参数
     */
    public void saveSysSetting(SysSettingDto sysSettingDto) {
        redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingDto);
    }

    /**
     * 存储用户Token
     */
    public void saveTokenUserInfoDto(TokenUserInfoDto dto) {
        if (dto == null || dto.getUserId() == null || dto.getToken() == null) return;
        initializeSessionMetadata(dto);
        long ttl = Constants.REDIS_KEY_EXPIRES_DAY * 2L;
        String sessionsKey = Constants.REDIS_KEY_WS_SESSIONS_USER + dto.getUserId();
        redisUtils.listRemove(sessionsKey, dto.getSessionId());
        redisUtils.listPush(sessionsKey, dto.getSessionId(), ttl);
        redisUtils.setEx(Constants.REDIS_KEY_WS_SESSION + dto.getSessionId(), dto, ttl);
        redisUtils.setEx(Constants.REDIS_KEY_WS_TOKEN + dto.getToken(), dto, ttl);
    }

    /**
     * 获取用户信息Token
     */
    public TokenUserInfoDto getTokenUserInfoDto(String token) {
        if (token == null || token.isBlank()) return null;
        TokenUserInfoDto dto = (TokenUserInfoDto) redisUtils.get(Constants.REDIS_KEY_WS_TOKEN + token);
        if (dto != null && (dto.getSessionId() == null || dto.getSessionId().isBlank())) {
            dto.setDeviceName("WeTalk 客户端（旧会话）");
            saveTokenUserInfoDto(dto);
        }
        return dto;
    }

    public void saveWebSocketTicket(String ticket, TokenUserInfoDto user) {
        redisUtils.setEx(Constants.REDIS_KEY_WS_TICKET + ticket, user, Constants.REDIS_KEY_EXPIRES_WS_TICKET);
    }

    public TokenUserInfoDto consumeWebSocketTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) return null;
        TokenUserInfoDto ticketUser = (TokenUserInfoDto) redisUtils.getAndDelete(Constants.REDIS_KEY_WS_TICKET + ticket);
        if (ticketUser == null || ticketUser.getToken() == null) return null;
        TokenUserInfoDto activeSession = getTokenUserInfoDto(ticketUser.getToken());
        if (activeSession == null || !Objects.equals(ticketUser.getUserId(), activeSession.getUserId())) return null;
        return activeSession;
    }

    /**
     * 获取用户Token by id
     */
    public TokenUserInfoDto getTokenUserInfoDtoByUserId(String userId) {
        List<TokenUserInfoDto> sessions = getUserSessions(userId);
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    public TokenUserInfoDto getTokenUserInfoDtoBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        TokenUserInfoDto dto = (TokenUserInfoDto) redisUtils.get(Constants.REDIS_KEY_WS_SESSION + sessionId);
        if (dto != null && (dto.getSessionId() == null || dto.getSessionId().isBlank())) {
            dto.setSessionId(sessionId);
            saveTokenUserInfoDto(dto);
        }
        return dto;
    }

    public List<TokenUserInfoDto> getUserSessions(String userId) {
        if (userId == null || userId.isBlank()) return List.of();
        migrateLegacySession(userId);
        String sessionsKey = Constants.REDIS_KEY_WS_SESSIONS_USER + userId;
        List<String> sessionIds = redisUtils.getQueueList(sessionsKey);
        if (sessionIds == null || sessionIds.isEmpty()) return List.of();

        Set<String> uniqueIds = new LinkedHashSet<>(sessionIds);
        List<TokenUserInfoDto> sessions = new ArrayList<>();
        for (String sessionId : uniqueIds) {
            TokenUserInfoDto dto = getTokenUserInfoDtoBySessionId(sessionId);
            if (dto == null || !userId.equals(dto.getUserId())) {
                redisUtils.listRemove(sessionsKey, sessionId);
                continue;
            }
            sessions.add(dto);
        }
        return sessions;
    }

    public TokenUserInfoDto removeUserSession(String userId, String sessionId) {
        TokenUserInfoDto dto = getTokenUserInfoDtoBySessionId(sessionId);
        if (dto == null || !userId.equals(dto.getUserId())) return null;
        redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN + dto.getToken(), Constants.REDIS_KEY_WS_SESSION + sessionId);
        redisUtils.listRemove(Constants.REDIS_KEY_WS_SESSIONS_USER + userId, sessionId);
        Object legacyToken = redisUtils.get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        if (dto.getToken().equals(legacyToken)) {
            redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        }
        return dto;
    }

    public List<TokenUserInfoDto> removeOtherUserSessions(String userId, String currentSessionId) {
        List<TokenUserInfoDto> removed = new ArrayList<>();
        for (TokenUserInfoDto dto : getUserSessions(userId)) {
            if (dto.getSessionId().equals(currentSessionId)) continue;
            TokenUserInfoDto deleted = removeUserSession(userId, dto.getSessionId());
            if (deleted != null) removed.add(deleted);
        }
        return removed;
    }

    public void updateUserSessionsNickName(String userId, String nickName) {
        for (TokenUserInfoDto dto : getUserSessions(userId)) {
            dto.setNickName(nickName);
            saveTokenUserInfoDto(dto);
        }
    }

    public void touchUserSession(String userId, String sessionId) {
        TokenUserInfoDto dto = getTokenUserInfoDtoBySessionId(sessionId);
        if (dto == null || !userId.equals(dto.getUserId())) return;
        long now = System.currentTimeMillis();
        if (dto.getLastActiveAt() != null && now - dto.getLastActiveAt() < 60_000) return;
        dto.setLastActiveAt(now);
        saveTokenUserInfoDto(dto);
    }

    /**
     * 清空用户 Token
     * 先查询 有没有 Token 再进行删除操作
     * 先删 dto 再删 Token
     */
    public void clearTokenUserInfoDto(String userId) {
        for (TokenUserInfoDto dto : getUserSessions(userId)) {
            removeUserSession(userId, dto.getSessionId());
        }
        Object legacyToken = redisUtils.get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        if (legacyToken instanceof String token) {
            redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN + token);
        }
        redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN_USERID + userId, Constants.REDIS_KEY_WS_SESSIONS_USER + userId);
    }

    private void migrateLegacySession(String userId) {
        Object legacyValue = redisUtils.get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        if (!(legacyValue instanceof String token)) return;
        TokenUserInfoDto dto = (TokenUserInfoDto) redisUtils.get(Constants.REDIS_KEY_WS_TOKEN + token);
        if (dto == null || !userId.equals(dto.getUserId())) {
            redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
            return;
        }
        if (dto.getSessionId() == null || dto.getSessionId().isBlank()) {
            dto.setDeviceName("WeTalk 客户端（旧会话）");
        }
        saveTokenUserInfoDto(dto);
    }

    private void initializeSessionMetadata(TokenUserInfoDto dto) {
        long now = System.currentTimeMillis();
        if (dto.getSessionId() == null || dto.getSessionId().isBlank()) {
            dto.setSessionId(UUID.randomUUID().toString());
        }
        if (dto.getDeviceName() == null || dto.getDeviceName().isBlank()) {
            dto.setDeviceName("未知设备");
        }
        if (dto.getCreatedAt() == null) dto.setCreatedAt(now);
        if (dto.getLastActiveAt() == null) dto.setLastActiveAt(now);
    }

    /**
     * 获取心跳
     */
    public Long getUserHeartBeat(String userId) {
        return (Long) redisUtils.get(Constants.REDIS_KEY_WS_USER_HEART_BEAT + userId);
    }

    /**
     * 存储心跳
     */
    public void saveUserHeartBeat(String userId) {
        redisUtils.setEx(Constants.REDIS_KEY_WS_USER_HEART_BEAT + userId, System.currentTimeMillis(), Constants.REDIS_KEY_EXPIRES_HEART_BEAT);

    }

    /**
     * 删除用户心跳
     */
    public void removeUserHeartBeat(String userId) {
        redisUtils.delete(Constants.REDIS_KEY_WS_USER_HEART_BEAT + userId);
    }

    /**
     * 删除联系人
     */
    public void removeUserContact(String userId, String contactId) {
        redisUtils.delete(Constants.REDIS_KEY_USER_CONTACT + userId, contactId);
    }

    /**
     * 清空联系人
     */
    public void deleteContactBatch(String userId) {
        redisUtils.delete(Constants.REDIS_KEY_USER_CONTACT + userId);
    }


    /**
     * 单独添加联系人
     */
    public void saveContact(String userId, String contactId) {
        List<String> userContactList = getUserContactList(userId);
        if (!userContactList.contains(contactId)) {
            redisUtils.listPush(Constants.REDIS_KEY_USER_CONTACT + userId, contactId, Constants.REDIS_KEY_EXPIRES_DAY * 2);
        }

    }


    /**
     * 批量添加联系人
     */
    public void saveContactBatch(String userId, List<String> contactIdList) {
        redisUtils.listPushAll(Constants.REDIS_KEY_USER_CONTACT + userId, contactIdList, Constants.REDIS_KEY_EXPIRES_DAY * 2);
    }

    /**
     * 获取联系人列表
     */
    public List<String> getUserContactList(String userId) {
        return redisUtils.getQueueList(Constants.REDIS_KEY_USER_CONTACT + userId);
    }




}
