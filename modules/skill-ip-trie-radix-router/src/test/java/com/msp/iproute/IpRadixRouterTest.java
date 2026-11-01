package com.msp.iproute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IpRadixRouter Unit Tests")
class IpRadixRouterTest {

    private IpRadixRouter<String> router;

    @BeforeEach
    void setUp() {
        router = new IpRadixRouter<>();
    }

    @Test
    @DisplayName("IPv4 Longest Prefix Match (LPM) selects the most specific route")
    void testIpv4LpmSelection() {
        router.addRoute("10.0.0.0/8", "Target-8");
        router.addRoute("10.1.0.0/16", "Target-16");
        router.addRoute("10.1.2.0/24", "Target-24");
        router.addRoute("10.1.2.4/32", "Target-Host-32");

        // 10.1.2.4 should match Target-Host-32
        assertEquals(Optional.of("Target-Host-32"), router.lookup("10.1.2.4"));

        // 10.1.2.5 should match Target-24
        assertEquals(Optional.of("Target-24"), router.lookup("10.1.2.5"));

        // 10.1.99.1 should match Target-16
        assertEquals(Optional.of("Target-16"), router.lookup("10.1.99.1"));

        // 10.200.1.1 should match Target-8
        assertEquals(Optional.of("Target-8"), router.lookup("10.200.1.1"));

        // 192.168.1.1 should have no match (Optional.empty)
        assertEquals(Optional.empty(), router.lookup("192.168.1.1"));
    }

    @Test
    @DisplayName("Default route (0.0.0.0/0) fallback when no specific route matches")
    void testIpv4DefaultRouteFallback() {
        router.addRoute("0.0.0.0/0", "Default-Gateway");
        router.addRoute("192.168.0.0/16", "Local-LAN");

        assertEquals(Optional.of("Local-LAN"), router.lookup("192.168.1.50"));
        assertEquals(Optional.of("Default-Gateway"), router.lookup("8.8.8.8"));
        assertEquals(Optional.of("Default-Gateway"), router.lookup("1.1.1.1"));
    }

    @Test
    @DisplayName("IPv6 Longest Prefix Match (LPM) and default route")
    void testIpv6LpmLookup() {
        router.addRoute("::/0", "IPv6-Default");
        router.addRoute("2001:db8::/32", "Target-DB8-32");
        router.addRoute("2001:db8:1::/48", "Target-DB8-48");
        router.addRoute("2001:db8:1:1::/64", "Target-DB8-64");

        assertEquals(Optional.of("Target-DB8-64"), router.lookup("2001:db8:1:1::abcd"));
        assertEquals(Optional.of("Target-DB8-48"), router.lookup("2001:db8:1:2::1"));
        assertEquals(Optional.of("Target-DB8-32"), router.lookup("2001:db8:ffff::1"));
        assertEquals(Optional.of("IPv6-Default"), router.lookup("2607:f8b0:4005:805::200e"));
    }

    @Test
    @DisplayName("Route removal works and updates route count")
    void testRemoveRoute() {
        router.addRoute("192.168.1.0/24", "LAN");
        router.addRoute("10.0.0.0/8", "Corp");
        assertEquals(2, router.routeCount());

        assertTrue(router.removeRoute("192.168.1.0/24"));
        assertEquals(1, router.routeCount());
        assertEquals(Optional.empty(), router.lookup("192.168.1.10"));
        assertEquals(Optional.of("Corp"), router.lookup("10.5.5.5"));

        // Removing non-existent route returns false
        assertFalse(router.removeRoute("192.168.1.0/24"));
        assertFalse(router.removeRoute("172.16.0.0/12"));
    }

    @Test
    @DisplayName("Clear removes all IPv4 and IPv6 routes")
    void testClear() {
        router.addRoute("192.168.1.0/24", "LAN");
        router.addRoute("2001:db8::/32", "V6");
        assertEquals(2, router.routeCount());

        router.clear();
        assertEquals(0, router.routeCount());
        assertEquals(Optional.empty(), router.lookup("192.168.1.10"));
        assertEquals(Optional.empty(), router.lookup("2001:db8::1"));
    }

    @Test
    @DisplayName("Updating existing route target overwrites value without incrementing count")
    void testUpdateExistingRoute() {
        router.addRoute("10.0.0.0/8", "Old-Target");
        assertEquals(1, router.routeCount());
        assertEquals(Optional.of("Old-Target"), router.lookup("10.1.2.3"));

        router.addRoute("10.0.0.0/8", "New-Target");
        assertEquals(1, router.routeCount());
        assertEquals(Optional.of("New-Target"), router.lookup("10.1.2.3"));
    }

    @Test
    @DisplayName("Implicit /32 or /128 prefix length for bare IP addresses")
    void testBareIpRoutes() {
        router.addRoute("192.168.1.100", "Specific-Host");
        assertEquals(1, router.routeCount());
        assertEquals(Optional.of("Specific-Host"), router.lookup("192.168.1.100"));
        assertEquals(Optional.empty(), router.lookup("192.168.1.101"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("Throws exception on null or blank inputs")
    void testNullOrBlankInputs(String input) {
        assertThrows(IllegalArgumentException.class, () -> router.addRoute(input, "target"));
        assertThrows(IllegalArgumentException.class, () -> router.lookup(input));
        assertThrows(IllegalArgumentException.class, () -> router.removeRoute(input));
    }

    @Test
    @DisplayName("Throws on invalid CIDR or IP format")
    void testInvalidFormats() {
        assertThrows(IllegalArgumentException.class, () -> router.addRoute("256.0.0.1/24", "target"));
        assertThrows(IllegalArgumentException.class, () -> router.addRoute("192.168.1.0/33", "target"));
        assertThrows(IllegalArgumentException.class, () -> router.addRoute("2001:db8::/129", "target"));
        assertThrows(IllegalArgumentException.class, () -> router.addRoute("not-an-ip/24", "target"));
        assertThrows(IllegalArgumentException.class, () -> router.addRoute("192.168.1.0/24/1", "target"));
        assertThrows(NullPointerException.class, () -> router.addRoute("10.0.0.0/8", null));
    }

    @Test
    @DisplayName("Thread safety under concurrent reads, writes, and removals")
    void testConcurrentOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String cidr = "10." + threadId + "." + (i % 256) + ".0/24";
                        String ip = "10." + threadId + "." + (i % 256) + ".15";
                        router.addRoute(cidr, "Worker-" + threadId);
                        Optional<String> match = router.lookup(ip);
                        if (match.isPresent()) {
                            successCounter.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(router.routeCount() > 0);
        assertTrue(successCounter.get() > 0);
    }
}
