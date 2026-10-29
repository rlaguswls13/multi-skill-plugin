package com.msp.result.validation;

/**
 * 단일 필드 또는 조건에 대한 유효성 검증 실패 상세 정보.
 *
 * @param fieldName     오류가 발생한 필드명 (null일 경우 전역 오류)
 * @param rejectedValue 거부된 입력 값
 * @param message       사용자 안내 오류 메시지
 */
public record ValidationError(
        String fieldName,
        Object rejectedValue,
        String message
) {
    public static ValidationError of(String fieldName, Object rejectedValue, String message) {
        return new ValidationError(fieldName, rejectedValue, message);
    }

    public static ValidationError global(String message) {
        return new ValidationError(null, null, message);
    }
}
