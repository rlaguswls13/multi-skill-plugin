package com.msp.anonymizer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 개인정보보호법 및 GDPR 컴플라이언스를 위한 데이터 비식별화(De-identification) 처리기.
 * K-익명성(범주화), 가명화(HMAC), 국소 섭동(노이즈), 마스킹 지원.
 */
public final class Anonymizer {

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^([가-힣a-zA-Z0-9]+[시|도])\\s+([가-힣a-zA-Z0-9]+[시|군|구])");
    private static final SecureRandom RANDOM = new SecureRandom();

    private Anonymizer() {}

    /**
     * 나이를 10년 단위 연령대로 범주화(Generalization)합니다. (예: 27 -> "20대", 32 -> "30대")
     */
    public static String generalizeAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative: " + age);
        }
        if (age < 10) {
            return "10대 미만";
        }
        int decade = (age / 10) * 10;
        return decade + "대";
    }

    /**
     * 주소를 시/도, 시/군/구 수준으로 범주화 절삭합니다.
     * 예: "서울특별시 강남구 역삼동 123-45" -> "서울특별시 강남구"
     */
    public static String generalizeAddress(String fullAddress) {
        if (fullAddress == null || fullAddress.isBlank()) {
            return "";
        }
        Matcher matcher = ADDRESS_PATTERN.matcher(fullAddress.trim());
        if (matcher.find()) {
            return matcher.group(1) + " " + matcher.group(2);
        }
        // 공백 기준 앞의 2단어 반환
        String[] parts = fullAddress.trim().split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];
        }
        return parts[0];
    }

    /**
     * 솔트(Secret Key)를 이용한 HMAC-SHA256 가명화(Pseudonymization) 토큰을 생성합니다.
     */
    public static String pseudonymize(String value, String salt) {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(salt, "salt must not be null");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to pseudonymize value", e);
        }
    }

    /**
     * 차분 프라이버시(Differential Privacy)를 위한 수치 국소 섭동(Local Perturbation).
     * 원래 값에 [-noiseRange, +noiseRange] 사이의 난수를 가산합니다.
     */
    public static double perturb(double value, double noiseRange) {
        if (noiseRange < 0) {
            throw new IllegalArgumentException("noiseRange must be non-negative: " + noiseRange);
        }
        double factor = (RANDOM.nextDouble() * 2.0) - 1.0; // [-1.0, 1.0]
        return value + (factor * noiseRange);
    }

    /**
     * 일반 문자열 부분 마스킹.
     * 앞 startKeep자리와 뒤 endKeep자리를 남기고 나머지를 maskChar로 치환합니다.
     */
    public static String mask(String text, int startKeep, int endKeep, char maskChar) {
        if (text == null) return null;
        int len = text.length();
        if (len <= startKeep + endKeep) {
            return String.valueOf(maskChar).repeat(len);
        }
        String prefix = text.substring(0, startKeep);
        String suffix = text.substring(len - endKeep);
        int maskLen = len - startKeep - endKeep;
        return prefix + String.valueOf(maskChar).repeat(maskLen) + suffix;
    }
}
