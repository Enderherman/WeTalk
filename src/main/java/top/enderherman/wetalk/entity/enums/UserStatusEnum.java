package top.enderherman.wetalk.entity.enums;

public enum UserStatusEnum {
    DISABLE(0, "禁用"),
    ENABLE(1, "启用");

    private Integer status;
    private String description;

    UserStatusEnum(Integer status, String description) {
        this.status = status;
        this.description = description;
    }

    public static UserStatusEnum getByStatus(Integer status) {
        for (UserStatusEnum item : UserStatusEnum.values()) {
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

    public void setDescription(String description) {
        this.description = description;
    }
}
