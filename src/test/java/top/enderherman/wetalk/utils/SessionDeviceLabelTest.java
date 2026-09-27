package top.enderherman.wetalk.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionDeviceLabelTest {

    @Test
    void identifiesChromiumBrowsersAndOperatingSystemsWithoutStoringRawUserAgent() {
        assertEquals("Chrome · Windows", SessionDeviceLabel.fromUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"));
        assertEquals("Edge · Windows", SessionDeviceLabel.fromUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"));
    }

    @Test
    void identifiesMobileSafariAndHandlesMissingUserAgent() {
        assertEquals("Safari · iOS", SessionDeviceLabel.fromUserAgent(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1"));
        assertEquals("未知设备", SessionDeviceLabel.fromUserAgent(null));
    }
}
