package com.msp.uaparser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3rd party 라이브러리 없이 순수 Java 21 정규식 매칭 기반으로
 * 브라우저, OS, 기기 유형, 봇 여부를 초고속 판별하는 User-Agent 파서.
 */
public final class UserAgentParser {

    private static final Pattern BOT_PATTERN = Pattern.compile(
            "(?i)(googlebot|bingbot|slurp|duckduckbot|baiduspider|yandexbot|twitterbot|facebookexternalhit|slackbot|curl|wget|postmanruntime)"
    );

    private static final Pattern EDGE_PATTERN = Pattern.compile("(?i)(?:Edge|Edg)/([0-9.]+)");
    private static final Pattern CHROME_PATTERN = Pattern.compile("(?i)(?:Chrome|CriOS)/([0-9.]+)");
    private static final Pattern FIREFOX_PATTERN = Pattern.compile("(?i)(?:Firefox|FxiOS)/([0-9.]+)");
    private static final Pattern OPERA_PATTERN = Pattern.compile("(?i)(?:OPR|Opera)/([0-9.]+)");
    private static final Pattern SAFARI_PATTERN = Pattern.compile("(?i)Version/([0-9.]+).*Safari");

    private UserAgentParser() {}

    public static UserAgent parse(String uaString) {
        if (uaString == null || uaString.isBlank()) {
            return new UserAgent("", "Unknown", "0.0", "Unknown", DeviceType.UNKNOWN, false, null);
        }

        String raw = uaString.trim();

        // 1. Bot check
        Matcher botMatcher = BOT_PATTERN.matcher(raw);
        if (botMatcher.find()) {
            String bot = botMatcher.group(1);
            return new UserAgent(raw, "Bot", "1.0", "Bot-Network", DeviceType.BOT, true, bot);
        }

        // 2. OS & Device Type check
        String os = "Unknown";
        DeviceType deviceType = DeviceType.DESKTOP;

        String lower = raw.toLowerCase();
        if (lower.contains("ipad")) {
            os = "iOS (iPad)";
            deviceType = DeviceType.TABLET;
        } else if (lower.contains("iphone")) {
            os = "iOS";
            deviceType = DeviceType.MOBILE;
        } else if (lower.contains("android")) {
            os = "Android";
            deviceType = lower.contains("mobile") ? DeviceType.MOBILE : DeviceType.TABLET;
        } else if (lower.contains("windows nt 10.0")) {
            os = "Windows 10/11";
            deviceType = DeviceType.DESKTOP;
        } else if (lower.contains("windows nt")) {
            os = "Windows";
            deviceType = DeviceType.DESKTOP;
        } else if (lower.contains("mac os x")) {
            os = "macOS";
            deviceType = DeviceType.DESKTOP;
        } else if (lower.contains("linux")) {
            os = "Linux";
            deviceType = DeviceType.DESKTOP;
        }

        // 3. Browser & Version check
        String browser = "Unknown";
        String version = "0.0";

        Matcher edgeMatcher = EDGE_PATTERN.matcher(raw);
        if (edgeMatcher.find()) {
            browser = "Edge";
            version = edgeMatcher.group(1);
        } else {
            Matcher operaMatcher = OPERA_PATTERN.matcher(raw);
            if (operaMatcher.find()) {
                browser = "Opera";
                version = operaMatcher.group(1);
            } else {
                Matcher chromeMatcher = CHROME_PATTERN.matcher(raw);
                if (chromeMatcher.find()) {
                    browser = "Chrome";
                    version = chromeMatcher.group(1);
                } else {
                    Matcher firefoxMatcher = FIREFOX_PATTERN.matcher(raw);
                    if (firefoxMatcher.find()) {
                        browser = "Firefox";
                        version = firefoxMatcher.group(1);
                    } else {
                        Matcher safariMatcher = SAFARI_PATTERN.matcher(raw);
                        if (safariMatcher.find()) {
                            browser = "Safari";
                            version = safariMatcher.group(1);
                        }
                    }
                }
            }
        }

        return new UserAgent(raw, browser, version, os, deviceType, false, null);
    }
}
