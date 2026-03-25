package top.enderherman.wetalk.entity.enums;

import lombok.Getter;
import top.enderherman.wetalk.utils.StringUtils;

@Getter
public enum UserContactApplyStatusEnum {
    INIT(0, "待处理"),
    PASS(1, "已同意"),
    REJECT(2, "已拒绝"),
    BLACKLIST(3, "已拉黑");

    private final Integer status;

    private final String desc;

    UserContactApplyStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static UserContactApplyStatusEnum getByStatus(String name) {
        try {
            if (StringUtils.isEmpty(name)) {
                return null;
            }
            return UserContactApplyStatusEnum.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static UserContactApplyStatusEnum getByStatus(Integer status) {
        for (UserContactApplyStatusEnum item : UserContactApplyStatusEnum.values()) {
            if (item.getStatus().equals(status)) {
                return item;
            }
        }
        return null;
    }
}
