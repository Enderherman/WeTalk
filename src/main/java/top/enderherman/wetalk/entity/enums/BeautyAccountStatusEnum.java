package top.enderherman.wetalk.entity.enums;

public enum BeautyAccountStatusEnum {
    NO_USE(0, "未使用"),
    USEED(1, "已使用");

    private Integer status;
    private String description;

    BeautyAccountStatusEnum(Integer status, String description) {
        this.status = status;
        this.description = description;
    }

    public static BeautyAccountStatusEnum getByStatus(Integer status) {
        for (BeautyAccountStatusEnum item : BeautyAccountStatusEnum.values()) {
            if (item.getStatus().equals(status)) {
                return item;
            }
        }
        return null;
    }


    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
