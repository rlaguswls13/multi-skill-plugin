package com.msp.ipreputation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IpReputationCheckerTest {

    @Test
    @DisplayName("단일 IP 차단 및 클린 IP 검증")
    void testSingleIpBlock() {
        IpReputationChecker checker = new IpReputationChecker()
                .blockIp("198.51.100.10", "Known Botnet IP");

        assertTrue(checker.isBlocked("198.51.100.10"));
        assertFalse(checker.isBlocked("198.51.100.11"));

        ReputationCheckResult res = checker.check("198.51.100.10");
        assertTrue(res.blocked());
        assertEquals("198.51.100.10/32", res.matchedRule());
        assertEquals("Known Botnet IP", res.reason());

        ReputationCheckResult clean = checker.check("198.51.100.11");
        assertFalse(clean.blocked());
        assertNull(clean.matchedRule());
        assertEquals("Clean IP", clean.reason());
    }

    @Test
    @DisplayName("CIDR 서브넷 차단 검증 (/24, /16, /8)")
    void testCidrSubnetBlocking() {
        IpReputationChecker checker = new IpReputationChecker()
                .blockCidr("192.168.1.0/24", "Internal subnet blocked")
                .blockCidr("10.0.0.0/8", "Private A-class blocked");

        assertTrue(checker.isBlocked("192.168.1.1"));
        assertTrue(checker.isBlocked("192.168.1.254"));
        assertFalse(checker.isBlocked("192.168.2.1"));

        assertTrue(checker.isBlocked("10.123.45.67"));
        assertFalse(checker.isBlocked("11.0.0.1"));
    }

    @Test
    @DisplayName("화이트리스트 우선권 검증 (블랙리스트 대역 내 특정 IP 허용)")
    void testWhitelistPriority() {
        IpReputationChecker checker = new IpReputationChecker()
                .blockCidr("192.168.1.0/24", "Blocked subnet")
                .allowIp("192.168.1.50", "Admin Gateway");

        // 서브넷 전체는 차단
        assertTrue(checker.isBlocked("192.168.1.49"));
        assertTrue(checker.isBlocked("192.168.1.51"));

        // 화이트리스트된 단일 IP는 허용
        assertFalse(checker.isBlocked("192.168.1.50"));

        ReputationCheckResult res = checker.check("192.168.1.50");
        assertFalse(res.blocked());
        assertEquals("192.168.1.50/32", res.matchedRule());
        assertEquals("Admin Gateway", res.reason());
    }

    @Test
    @DisplayName("유효하지 않은 IPv4 및 CIDR 파싱 시 예외 발생 검증")
    void testInvalidIpAndCidr() {
        IpReputationChecker checker = new IpReputationChecker();

        assertThrows(IllegalArgumentException.class, () -> checker.blockIp("999.999.999.999"));
        assertThrows(IllegalArgumentException.class, () -> checker.blockIp("abc.def.ghi.jkl"));
        assertThrows(IllegalArgumentException.class, () -> checker.blockIp("192.168.1"));
        assertThrows(IllegalArgumentException.class, () -> checker.blockCidr("192.168.1.0/33"));
        assertThrows(IllegalArgumentException.class, () -> checker.blockCidr("192.168.1.0/-1"));
        assertThrows(IllegalArgumentException.class, () -> checker.isBlocked("not-an-ip"));
    }

    @Test
    @DisplayName("CIDR /0 전체 대역 차단 및 /32 특정 IP 단독 차단")
    void testBoundaryPrefixLengths() {
        IpReputationChecker checker = new IpReputationChecker()
                .blockCidr("0.0.0.0/0", "Block all by default")
                .allowCidr("8.8.8.0/24", "Allow Google DNS subnet");

        // 8.8.8.8은 allow subnet에 포함되어 통과
        assertFalse(checker.isBlocked("8.8.8.8"));
        // 그 외 모든 IP는 차단
        assertTrue(checker.isBlocked("1.1.1.1"));
        assertTrue(checker.isBlocked("142.250.190.46"));
    }

    @Test
    @DisplayName("스레드 동시성 안전성 검증")
    void testConcurrency() throws InterruptedException {
        IpReputationChecker checker = new IpReputationChecker()
                .blockCidr("10.0.0.0/16");

        int threadCount = 10;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger blockedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String testIp = "10.0." + (threadId % 256) + "." + (j % 256);
                        if (checker.isBlocked(testIp)) {
                            blockedCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * operationsPerThread, blockedCount.get());
    }

    @Test
    @DisplayName("clear 및 룰 카운트 동작 검증")
    void testClearAndCounts() {
        IpReputationChecker checker = new IpReputationChecker()
                .blockIp("1.2.3.4")
                .allowIp("5.6.7.8");

        assertEquals(1, checker.getBlockRuleCount());
        assertEquals(1, checker.getAllowRuleCount());

        checker.clear();
        assertEquals(0, checker.getBlockRuleCount());
        assertEquals(0, checker.getAllowRuleCount());
        assertFalse(checker.isBlocked("1.2.3.4"));
    }
}
