package com.msp.result.error;

import java.util.Objects;

/**
 * 런타임에 동적으로 정의할 수 있는 사용자 정의 에러 코드 구현체.
 */
public record CustomErrorCode(
        String code,
        String message,
        int httpStatus
) implements ErrorCode {

    public CustomErrorCode {
        Objects.requireNonNull(code, "code는 null일 수 없습니다.");
        Objects.requireNonNull(message, "message는 null일 수 없습니다.");
    }

    public static CustomErrorCode of(String code, String message) {
        return new CustomErrorCode(code, message, 400);
    }

    public static CustomErrorCode of(String code, String message, int httpStatus) {
        return new CustomErrorCode(code, message, httpStatus);
    }
}
