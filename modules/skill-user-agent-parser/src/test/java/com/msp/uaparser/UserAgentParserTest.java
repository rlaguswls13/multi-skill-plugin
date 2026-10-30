package com.msp.uaparser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserAgentParserTest {

    @Test
    @DisplayName("Parse Windows Chrome Desktop User Agent")
    void testWindowsChrome() {
        String uaStr = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        UserAgent ua = UserAgentParser.parse(uaStr);

        assertEquals("Chrome", ua.browser());
        assertEquals("120.0.0.0", ua.browserVersion());
        assertEquals("Windows 10/11", ua.os());
        assertEquals(DeviceType.DESKTOP, ua.deviceType());
        assertFalse(ua.isBot());
    }

    @Test
    @DisplayName("Parse iPhone Safari Mobile User Agent")
    void testIPhoneSafari() {
        String uaStr = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1";
        UserAgent ua = UserAgentParser.parse(uaStr);

        assertEquals("Safari", ua.browser());
        assertEquals("17.1", ua.browserVersion());
        assertEquals("iOS", ua.os());
        assertEquals(DeviceType.MOBILE, ua.deviceType());
        assertFalse(ua.isBot());
    }

    @Test
    @DisplayName("Parse iPad Tablet User Agent")
    void testIPad() {
        String uaStr = "Mozilla/5.0 (iPad; CPU OS 16_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1";
        UserAgent ua = UserAgentParser.parse(uaStr);

        assertEquals(DeviceType.TABLET, ua.deviceType());
        assertEquals("iOS (iPad)", ua.os());
    }

    @Test
    @DisplayName("Parse Googlebot Crawler")
    void testGooglebot() {
        String uaStr = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
        UserAgent ua = UserAgentParser.parse(uaStr);

        assertTrue(ua.isBot());
        assertEquals(DeviceType.BOT, ua.deviceType());
        assertTrue(ua.botName().equalsIgnoreCase("googlebot"));
    }

    @Test
    @DisplayName("Parse Edge Desktop User Agent")
    void testEdge() {
        String uaStr = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.2210.91";
        UserAgent ua = UserAgentParser.parse(uaStr);

        assertEquals("Edge", ua.browser());
        assertEquals("120.0.2210.91", ua.browserVersion());
    }
}
