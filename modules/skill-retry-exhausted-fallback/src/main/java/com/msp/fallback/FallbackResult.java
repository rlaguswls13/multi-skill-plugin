package com.msp.fallback;

/**
 * 폴백 실행 결과 래퍼.
 */
public record FallbackResult<T>(
        T value,
        boolean isFallback,
        Throwable rootCause,
        String strategyName
) {
    public static <T> FallbackResult<T> success(T value) {
        return new FallbackResult<>(value, false, null, "PRIMARY");
    }

    public static <T> FallbackResult<T> fallback(T value, Throwable cause, String strategyName) {
        return new FallbackResult<>(value, true, cause, strategyName);
    }
}
