package com.msp.uaparser;

/**
 * 파싱된 User-Agent 구조화 정보 레코드.
 */
public record UserAgent(
        String raw,
        String browser,
        String browserVersion,
        String os,
        DeviceType deviceType,
        boolean isBot,
        String botName
) {}
