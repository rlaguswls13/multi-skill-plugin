package com.msp.ipgeo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpRangeMatcherTest {

    @Test
    @DisplayName("CIDR matching test")
    void testCidrMatches() {
        Cidr cidr = Cidr.of("192.168.1.0/24");
        assertTrue(cidr.matches("192.168.1.1"));
        assertTrue(cidr.matches("192.168.1.254"));
        assertFalse(cidr.matches("192.168.2.1"));
        assertFalse(cidr.matches("10.0.0.1"));
    }

    @Test
    @DisplayName("Private IP classification test")
    void testPrivateIps() {
        assertTrue(IpClassifier.isPrivate("10.10.1.5"));
        assertTrue(IpClassifier.isPrivate("172.16.50.1"));
        assertTrue(IpClassifier.isPrivate("192.168.0.100"));
        assertFalse(IpClassifier.isPrivate("8.8.8.8"));
        assertFalse(IpClassifier.isPrivate("1.1.1.1"));
    }

    @Test
    @DisplayName("Loopback IP test")
    void testLoopbackIps() {
        assertTrue(IpClassifier.isLoopback("127.0.0.1"));
        assertTrue(IpClassifier.isLoopback("127.1.2.3"));
        assertTrue(IpClassifier.isLoopback("::1"));
        assertTrue(IpClassifier.isLoopback("localhost"));
        assertFalse(IpClassifier.isLoopback("192.168.1.1"));
    }

    @Test
    @DisplayName("Public IP test")
    void testPublicIps() {
        assertTrue(IpClassifier.isPublic("8.8.8.8"));
        assertTrue(IpClassifier.isPublic("203.238.181.1"));
        assertFalse(IpClassifier.isPublic("192.168.1.1"));
        assertFalse(IpClassifier.isPublic("127.0.0.1"));
    }

    @Test
    @DisplayName("CidrTable lookup test")
    void testCidrTable() {
        CidrTable<String> table = new CidrTable<>();
        table.add("203.0.113.0/24", "KR-Seoul");
        table.add("198.51.100.0/24", "US-East");

        assertEquals("KR-Seoul", table.find("203.0.113.50").orElse(null));
        assertEquals("US-East", table.find("198.51.100.200").orElse(null));
        assertTrue(table.find("8.8.8.8").isEmpty());
    }
}
