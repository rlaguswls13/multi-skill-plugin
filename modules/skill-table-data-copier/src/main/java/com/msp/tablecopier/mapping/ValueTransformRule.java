package com.msp.tablecopier.mapping;

import java.util.Locale;

/**
 * 열 데이터를 원하는 타겟 컬럼으로 치환할 때 적용할 값 변환/정제 규칙
 */
public enum ValueTransformRule {
    /** 변환 없음 */
    NONE,
    /** 앞뒤 공백 제거 */
    TRIM,
    /** 대문자 변환 (TRIM 포함) */
    UPPER,
    /** 소문자 변환 (TRIM 포함) */
    LOWER,
    /** 숫자(0-9, -, .)만 추출 */
    DIGITS_ONLY,
    /** 빈 문자열일 경우 null로 치환 */
    NULL_IF_EMPTY,
    /** 빈 문자열일 경우 defaultValue로 치환 */
    DEFAULT_IF_EMPTY;

    public Object apply(String rawValue, String defaultValue) {
        String safe = rawValue == null ? "" : rawValue;
        return switch (this) {
            case NONE -> safe;
            case TRIM -> safe.trim();
            case UPPER -> safe.trim().toUpperCase(Locale.ROOT);
            case LOWER -> safe.trim().toLowerCase(Locale.ROOT);
            case DIGITS_ONLY -> safe.replaceAll("[^0-9.-]", "");
            case NULL_IF_EMPTY -> safe.trim().isEmpty() ? null : safe.trim();
            case DEFAULT_IF_EMPTY -> safe.trim().isEmpty() ? defaultValue : safe.trim();
        };
    }
}
