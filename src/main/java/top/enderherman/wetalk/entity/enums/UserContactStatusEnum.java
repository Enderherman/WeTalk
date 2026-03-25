package top.enderherman.wetalk.entity.enums;

import top.enderherman.wetalk.utils.StringUtils;

public enum UserContactStatusEnum {
    NOT_FRIEND(0, "非好友"),
    FRIEND(1, "好友"),
    DEL(2, "已册除好友"),
    DEL_BE(3, "被好友册除"),
    BLACKLIST(4, "已拉黑好友"),
    BLACKLIST_BE(5, "被好友拉黑"),
    DEL_BE_FIRST(6, "被好友第一次直接删除"),
    BLACKLIST_BE_FIRST(7, "被好友第一次直接拉黑");


    private final Integer status;
    private final String description;

    UserContactStatusEnum(Integer status, String description) {
        this.status = status;
        this.description = description;
    }

    public static UserContactStatusEnum getByName(String name) {
        try {
            if (StringUtils.isEmpty(name)) {
                return null;
            }
            return UserContactStatusEnum.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    public static UserContactStatusEnum getByStatus(Integer status) {
        for (UserContactStatusEnum item : UserContactStatusEnum.values()) {
            if (item.getStatus().equals(status)) {
                return item;
            }
        }
        return null;
    }

    public Integer getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }
}

