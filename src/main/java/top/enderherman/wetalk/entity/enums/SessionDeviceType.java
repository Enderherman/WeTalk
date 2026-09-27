package top.enderherman.wetalk.entity.enums;

public enum SessionDeviceType {
    DESKTOP("desktop"),
    BROWSER("browser");

    private final String value;

    SessionDeviceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SessionDeviceType fromValue(String value) {
        for (SessionDeviceType type : values()) {
            if (type.value.equalsIgnoreCase(value)) return type;
        }
        return null;
    }
}
