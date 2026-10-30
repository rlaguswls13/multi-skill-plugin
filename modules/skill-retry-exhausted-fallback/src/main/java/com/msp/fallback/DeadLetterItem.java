package com.msp.fallback;

import java.time.Instant;

/**
 * 최종 실패 격리(Dead-Letter) 레코드.
 */
public record DeadLetterItem<T>(
        String identifier,
        T payload,
        Throwable cause,
        Instant timestamp
) {}
