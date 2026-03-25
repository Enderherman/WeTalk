package top.enderherman.wetalk.entity.enums;

import lombok.Getter;

@Getter
public enum AppUpdateStatusEnum {

    INIT(0, "未发布"),
    GRAYSCALE(1, "灰度发布"),
    ALL(2, "全网发布");

    private final Integer status;
    private final String description;

    AppUpdateStatusEnum(int status, String description) {
        this.status = status;
        this.description = description;
    }

    public static AppUpdateStatusEnum getByStatus(Integer status) {
        for (AppUpdateStatusEnum item : AppUpdateStatusEnum.values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }
}
