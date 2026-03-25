package top.enderherman.wetalk.entity.enums;


import lombok.Getter;

@Getter
public enum DateTimePatternEnum {
    YYYY_MM_DD_HH_MM_SS("yyyy-MM-dd HH:mm:ss"),
    YYYY_MM_DD("yyyy-MM-dd"),
    YYYY_MM("yyyyMM"),;

    private final String pattern;

    DateTimePatternEnum(String pattern) {
        this.pattern = pattern;
    }

}
