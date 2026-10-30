package com.msp.fallback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FallbackPipelineTest {

    @Test
    @DisplayName("Primary success returns normal result")
    void testPrimarySuccess() {
        FallbackResult<String> result = FallbackPipeline.of(() -> "SUCCESS")
                .fallbackValue("FALLBACK")
                .execute();

        assertFalse(result.isFallback());
        assertEquals("SUCCESS", result.value());
        assertNull(result.rootCause());
    }

    @Test
    @DisplayName("Primary failure triggers fallback value and records to DeadLetterQueue")
    void testPrimaryFailureFallbackAndDlq() {
        DeadLetterQueue<String> dlq = new DeadLetterQueue<>(10);

        FallbackResult<String> result = FallbackPipeline.<String>of(() -> {
                    throw new RuntimeException("DB Connection Timeout");
                })
                .fallbackValue("DEFAULT_CACHE_DATA")
                .deadLetterQueue(dlq, "order-sync-task-99")
                .execute();

        assertTrue(result.isFallback());
        assertEquals("DEFAULT_CACHE_DATA", result.value());
        assertNotNull(result.rootCause());
        assertEquals("DB Connection Timeout", result.rootCause().getMessage());

        // Check DLQ
        assertEquals(1, dlq.size());
        DeadLetterItem<String> item = dlq.snapshot().get(0);
        assertEquals("order-sync-task-99", item.identifier());
        assertEquals("DEFAULT_CACHE_DATA", item.payload());
        assertEquals("DB Connection Timeout", item.cause().getMessage());
    }

    @Test
    @DisplayName("Fallback function receives throwable cause")
    void testFallbackFunction() {
        FallbackResult<String> result = FallbackPipeline.<String>of(() -> {
                    throw new IllegalArgumentException("Invalid ID 404");
                })
                .fallbackFunction(ex -> "ERROR_HANDLED: " + ex.getMessage())
                .execute();

        assertTrue(result.isFallback());
        assertEquals("ERROR_HANDLED: Invalid ID 404", result.value());
    }
}
