package top.enderherman.wetalk.entity.po;

import lombok.Data;
import top.enderherman.wetalk.entity.enums.UserContactTypeEnum;

import java.io.Serializable;


/**
 * 会话用户表
 */
@Data
public class ChatSessionUser implements Serializable {


    /**
     * 用户id
     */
    private String userId;

    /**
     * 联系人id
     */
    private String contactId;

    /**
     * 会话id
     */
    private String sessionId;

    /**
     * 联系人名称
     */
    private String contactName;

    /**
     * 最后消息
     */
    private String lastMessage;

    /**
     * 最后时间
     */
    private Long lastReceiveTime;

    /**
     * 聊天类型
     */
    private Integer contactType;

    /**
     * 群组人数
     */
    private Integer memberCount;


    public Integer getContactType() {
        return UserContactTypeEnum.getByPrefix(contactId).getType();
    }


    @Override
    public String toString() {
        return "用户id:" + (userId == null ? "空" : userId) + "，联系人id:" + (contactId == null ? "空" : contactId) + "，会话id:" + (sessionId == null ? "空" : sessionId) + "，联系人名称:" + (contactName == null ? "空" : contactName);
    }
}
