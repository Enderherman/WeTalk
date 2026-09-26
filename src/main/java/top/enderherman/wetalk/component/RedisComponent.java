package top.enderherman.wetalk.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.SysSettingDto;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.utils.RedisUtils;

import jakarta.annotation.Resource;
import java.util.List;

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
        String previous = (String) redisUtils.get(Constants.REDIS_KEY_WS_TOKEN_USERID + dto.getUserId());
        if (previous != null && !previous.equals(dto.getToken())) {
            redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN + previous);
        }
        // 1.key: userId val: token
        redisUtils.setEx(Constants.REDIS_KEY_WS_TOKEN_USERID + dto.getUserId(), dto.getToken(), Constants.REDIS_KEY_EXPIRES_DAY * 2);

        // 2.key: token val: dto
        redisUtils.setEx(Constants.REDIS_KEY_WS_TOKEN + dto.getToken(), dto, Constants.REDIS_KEY_EXPIRES_DAY * 2);
    }

    /**
     * 获取用户信息Token
     */
    public TokenUserInfoDto getTokenUserInfoDto(String token) {
        return (TokenUserInfoDto) redisUtils.get(Constants.REDIS_KEY_WS_TOKEN + token);
    }

    /**
     * 获取用户Token by id
     */
    public TokenUserInfoDto getTokenUserInfoDtoByUserId(String userId) {

        String token = (String) redisUtils.get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        return getTokenUserInfoDto(token);
    }

    /**
     * 清空用户 Token
     * 先查询 有没有 Token 再进行删除操作
     * 先删 dto 再删 Token
     */
    public void clearTokenUserInfoDto(String userId) {
        String userTokenKey = Constants.REDIS_KEY_WS_TOKEN_USERID + userId;
        String token = (String) redisUtils.get(userTokenKey);
        if (token != null) {
            redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN + token);
        }
        redisUtils.delete(userTokenKey);
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
