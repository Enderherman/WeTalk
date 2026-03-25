package top.enderherman.wetalk.entity.enums;



import lombok.Getter;


@Getter
public enum AppUpdateFileTypeEnum {

    LOCAL(0, "本地"),
    OUTER_LINK(1, "外链");

    private final Integer type;
    private final String description;

    AppUpdateFileTypeEnum(int type, String description) {
        this.type = type;
        this.description = description;
    }

    public static AppUpdateFileTypeEnum getByType(Integer type) {
        for (AppUpdateFileTypeEnum at : AppUpdateFileTypeEnum.values()) {
            if (at.type.equals(type)) {
                return at;
            }
        }
        return null;
    }
}
