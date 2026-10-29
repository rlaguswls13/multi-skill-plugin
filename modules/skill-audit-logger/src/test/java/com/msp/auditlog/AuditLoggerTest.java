package com.msp.auditlog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AuditLoggerTest {

    @Test
    @DisplayName("AuditEvent builder populates fields and sensible defaults")
    void testAuditEventBuilder() {
        Instant now = Instant.parse("2026-10-30T04:00:00Z");
        AuditEvent event = AuditEvent.builder()
                .id("evt-1234")
                .timestamp(now)
                .actor("admin")
                .action("ORDER_CANCEL")
                .resource("Order/999")
                .clientIp("127.0.0.1")
                .status("SUCCESS")
                .detail("reason", "customer request")
                .diff("status", "PENDING", "CANCELLED")
                .build();

        assertEquals("evt-1234", event.id());
        assertEquals(now, event.timestamp());
        assertEquals("admin", event.actor());
        assertEquals("ORDER_CANCEL", event.action());
        assertEquals("Order/999", event.resource());
        assertEquals("127.0.0.1", event.clientIp());
        assertEquals("SUCCESS", event.status());
        assertEquals("customer request", event.details().get("reason"));
        assertEquals(1, event.diffs().size());
        assertEquals("status", event.diffs().get(0).field());
        assertEquals("PENDING", event.diffs().get(0).oldValue());
        assertEquals("CANCELLED", event.diffs().get(0).newValue());
    }

    @Test
    @DisplayName("DefaultDataMasker masks sensitive key names completely")
    void testSensitiveKeyMasking() {
        DataMasker masker = DataMasker.defaultMasker();

        assertEquals("******", masker.mask("password", "supersecret123"));
        assertEquals("******", masker.mask("apiKey", "ak_live_987654321"));
        assertEquals("******", masker.mask("user_token", "eyJhbGciOi..."));
        assertEquals("******", masker.mask("card_number", "4111222233334444"));

        // Non-sensitive key passes value through unless pattern matches
        assertEquals("standardValue", masker.mask("username", "standardValue"));
    }

    @Test
    @DisplayName("DefaultDataMasker masks sensitive string patterns: cards, RRN, phone, email")
    void testPatternMasking() {
        DataMasker masker = DataMasker.defaultMasker();

        // Card number
        Object maskedCard = masker.mask("comment", "User paid with 4111-2222-3333-4444 yesterday");
        assertEquals("User paid with 4111-****-****-4444 yesterday", maskedCard);

        // Korean RRN
        Object maskedRrn = masker.mask("memo", "Resident ID: 900101-1234567");
        assertEquals("Resident ID: 900101-1******", maskedRrn);

        // Korean Phone
        Object maskedPhone = masker.mask("note", "Contact at 010-1234-5678 immediately");
        assertEquals("Contact at 010-****-5678 immediately", maskedPhone);

        // Email
        Object maskedEmail = masker.mask("note", "Send copy to minjun.kim@msp.com please");
        assertEquals("Send copy to mi***@msp.com please", maskedEmail);
    }

    @Test
    @DisplayName("JSON audit formatting with nested masking")
    void testJsonFormatting() {
        AuditLogger.InMemoryAuditLogger inMemory = AuditLogger.inMemory(AuditFormat.JSON);

        AuditEvent event = AuditEvent.builder()
                .id("e-1")
                .timestamp(Instant.parse("2026-10-30T04:00:00Z"))
                .actor("user@msp.com")
                .action("LOGIN_ATTEMPT")
                .clientIp("192.168.1.10")
                .status("SUCCESS")
                .detail("password", "PlainPassword123")
                .detail("contact", "010-9999-8888")
                .diff("userPassword", "oldPwdSecret", "newPwdSecret")
                .build();

        inMemory.log(event);

        List<String> records = inMemory.getRecords();
        assertEquals(1, records.size());
        String json = records.get(0);

        assertTrue(json.startsWith("{\"id\":\"e-1\""));
        assertTrue(json.contains("\"password\":\"******\""));
        assertFalse(json.contains("PlainPassword123"));
        assertTrue(json.contains("010-****-8888"));
        assertTrue(json.contains("\"oldValue\":\"******\""));
        assertTrue(json.contains("\"newValue\":\"******\""));
    }

    @Test
    @DisplayName("Key-Value audit formatting")
    void testKeyValueFormatting() {
        AuditLogger.InMemoryAuditLogger inMemory = AuditLogger.inMemory(AuditFormat.KEY_VALUE);

        AuditEvent event = AuditEvent.builder()
                .id("e-2")
                .timestamp(Instant.parse("2026-10-30T04:00:00Z"))
                .actor("alice")
                .action("UPDATE_ROLE")
                .resource("User/50")
                .status("SUCCESS")
                .detail("role", "ADMIN")
                .diff("role", "MEMBER", "ADMIN")
                .build();

        inMemory.log(event);

        List<String> records = inMemory.getRecords();
        assertEquals(1, records.size());
        String kv = records.get(0);

        assertTrue(kv.contains("id=\"e-2\""));
        assertTrue(kv.contains("actor=\"alice\""));
        assertTrue(kv.contains("action=\"UPDATE_ROLE\""));
        assertTrue(kv.contains("resource=\"User/50\""));
        assertTrue(kv.contains("details={role=\"ADMIN\"}"));
        assertTrue(kv.contains("diffs=[role:(\"MEMBER\" -> \"ADMIN\")]"));
    }

    @Test
    @DisplayName("Thread-safe logging from concurrent threads")
    void testConcurrentLogging() throws Exception {
        AuditLogger.InMemoryAuditLogger inMemory = AuditLogger.inMemory(AuditFormat.JSON);
        int threadCount = 10;
        int perThread = 20;
        var executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        inMemory.log(AuditEvent.builder()
                                .id("id-" + threadId + "-" + j)
                                .action("ACTION_" + j)
                                .detail("secretKey", "secretVal")
                                .build());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * perThread, inMemory.getRecords().size());
    }
}
