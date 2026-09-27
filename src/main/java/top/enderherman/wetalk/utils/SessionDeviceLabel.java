package top.enderherman.wetalk.utils;

import java.util.Locale;

public final class SessionDeviceLabel {

    private SessionDeviceLabel() {
    }

    public static String fromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知设备";
        }

        String value = userAgent.toLowerCase(Locale.ROOT);
        String browser;
        if (value.contains("electron")) browser = "WeTalkApp";
        else if (value.contains("edg/")) browser = "Edge";
        else if (value.contains("firefox/")) browser = "Firefox";
        else if (value.contains("chrome/") || value.contains("chromium/")) browser = "Chrome";
        else if (value.contains("safari/")) browser = "Safari";
        else browser = "浏览器";

        String operatingSystem;
        if (value.contains("android")) operatingSystem = "Android";
        else if (value.contains("iphone") || value.contains("ipad") || value.contains("ios")) operatingSystem = "iOS";
        else if (value.contains("windows")) operatingSystem = "Windows";
        else if (value.contains("mac os") || value.contains("macintosh")) operatingSystem = "macOS";
        else if (value.contains("linux")) operatingSystem = "Linux";
        else operatingSystem = "其他设备";

        return browser + " · " + operatingSystem;
    }
}
