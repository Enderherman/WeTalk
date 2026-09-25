package top.enderherman.wetalk.entity.query;


import lombok.Data;

import java.util.List;

/**
 * 聊天消息表参数
 */
@Data
public class ChatMessageQuery extends BaseParam {


    /**
     * id
     */
    private Integer messageId;

    /** 查询此消息之前的历史记录 */
    private Integer beforeMessageId;

    /**
     * 会话id
     */
    private String sessionId;

    private String sessionIdFuzzy;

    /**
     * 消息类型
     */
    private Integer messageType;

    /**
     * 消息内容
     */
    private String messageContent;

    private String messageContentFuzzy;

    /**
     * 发送人id
     */
    private String sendUserId;

    private String sendUserIdFuzzy;

    /**
     * 发送人昵称
     */
    private String sendUserNickName;

    private String sendUserNickNameFuzzy;

    /**
     * 发送时间
     */
    private Long sendTime;

    /**
     * 联系人id
     */
    private String contactId;

    private String contactIdFuzzy;

    /**
     * 联系人类型 0:单聊 1:群聊
     */
    private Integer contactType;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件名
     */
    private String fileName;

    private String fileNameFuzzy;

    /**
     * 文件类型
     */
    private Integer fileType;

    /**
     * 状态 0:正在发送 1:已发送
     */
    private Integer status;

    /**
     * 联系人列表
     */
    private List<String> contactIdList;

    /**
     * 最后登录时间
     */
    private Long  lastReceiveTime;

}
