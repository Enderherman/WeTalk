package top.enderherman.wetalk.entity.enums;

import top.enderherman.wetalk.utils.StringUtils;

public enum JoinTypeEnum {
    PASS(0, "直接通过"),
    APPLY(1, "需要审核");

    private final Integer type;
    private final String description;

    JoinTypeEnum(Integer type, String description) {
        this.type = type;
        this.description = description;
    }

    public Integer getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public static JoinTypeEnum getByName(String name) {
        try {
            if (StringUtils.isEmpty(name)) {
                return null;
            }
            return JoinTypeEnum.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    public static JoinTypeEnum getByType(Integer joinType) {
        for (JoinTypeEnum item : JoinTypeEnum.values()) {
            if (item.getType().equals(joinType)) {
                return item;
            }
        }
        return null;
    }
}
