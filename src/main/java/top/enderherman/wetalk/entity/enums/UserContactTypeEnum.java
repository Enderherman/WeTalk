package top.enderherman.wetalk.entity.enums;

import top.enderherman.wetalk.utils.StringUtils;

public enum UserContactTypeEnum {
    USER(0, "U", "好友"),
    GROUP(1, "G", "群聊");

    private  Integer type;
    private  String prefix;
    private  String description;

    UserContactTypeEnum(Integer type, String prefix, String description) {
        this.type = type;
        this.prefix = prefix;
        this.description = description;
    }

    public Integer getType() {
        return type;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDescription() {
        return description;
    }

    public static UserContactTypeEnum getByName(String name) {
        try {
            if (StringUtils.isEmpty(name)) {
                return null;
            }
            return UserContactTypeEnum.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    public static UserContactTypeEnum getByPrefix(String prefix) {
        try {
            if (StringUtils.isEmpty(prefix) || prefix.trim().length() == 0) {
                return null;
            }
            prefix = prefix.substring(0, 1);
            for (UserContactTypeEnum typeEnum : UserContactTypeEnum.values()) {
                if (typeEnum.getPrefix().equals(prefix)) {
                    return typeEnum;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
