package top.enderherman.wetalk.entity.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserSessionVO implements Serializable {
    private String sessionId;
    private String deviceName;
    private String deviceType;
    private Long createdAt;
    private Long lastActiveAt;
    private boolean current;
}
