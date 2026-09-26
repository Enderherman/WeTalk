package top.enderherman.wetalk.entity.enums;

import lombok.Getter;

@Getter
public enum MessageStatusEnum {
    SENDING(0, "发送中"),
    SENT(1, "已发送"),
    AI_CANCELLED(2, "AI 生成已停止"),
    AI_FAILED(3, "AI 生成失败");


    private final Integer status;
    private final String desc;

    MessageStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static MessageStatusEnum getByStatus(Integer status) {
        for (MessageStatusEnum item : MessageStatusEnum.values()) {
            if (item.getStatus().equals(status)) {
                return item;
            }
        }
        return null;
    }
}
