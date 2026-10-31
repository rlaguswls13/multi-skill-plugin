package com.msp.cidr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CidrCalculator Tests")
class CidrCalculatorTest {

    @Test
    @DisplayName("Calculate standard /24 subnet")
    void testStandardSlash24() {
        SubnetInfo info = CidrCalculator.calculate("192.168.1.0/24");
        assertEquals("192.168.1.0/24", info.cidr());
        assertEquals("192.168.1.0", info.networkAddress());
        assertEquals("192.168.1.255", info.broadcastAddress());
        assertEquals("255.255.255.0", info.netmask());
        assertEquals("192.168.1.1", info.firstHost());
        assertEquals("192.168.1.254", info.lastHost());
        assertEquals(256L, info.totalHosts());
        assertEquals(254L, info.usableHosts());
        assertEquals(24, info.prefixLength());
    }

    @Test
    @DisplayName("Calculate /16 subnet with non-zero host bits normalized")
    void testSlash16WithHostBits() {
        SubnetInfo info = CidrCalculator.calculate("10.50.25.100/16");
        assertEquals("10.50.0.0/16", info.cidr());
        assertEquals("10.50.0.0", info.networkAddress());
        assertEquals("10.50.255.255", info.broadcastAddress());
        assertEquals("255.255.0.0", info.netmask());
        assertEquals("10.50.0.1", info.firstHost());
        assertEquals("10.50.255.254", info.lastHost());
        assertEquals(65536L, info.totalHosts());
        assertEquals(65534L, info.usableHosts());
        assertEquals(16, info.prefixLength());
    }

    @Test
    @DisplayName("Calculate edge /32 single-host route")
    void testSlash32SingleHost() {
        SubnetInfo info = CidrCalculator.calculate("172.16.0.42/32");
        assertEquals("172.16.0.42/32", info.cidr());
        assertEquals("172.16.0.42", info.networkAddress());
        assertEquals("172.16.0.42", info.broadcastAddress());
        assertEquals("255.255.255.255", info.netmask());
        assertEquals("172.16.0.42", info.firstHost());
        assertEquals("172.16.0.42", info.lastHost());
        assertEquals(1L, info.totalHosts());
        assertEquals(1L, info.usableHosts());
        assertEquals(32, info.prefixLength());
    }

    @Test
    @DisplayName("Calculate edge /31 point-to-point link (RFC 3021)")
    void testSlash31PointToPoint() {
        SubnetInfo info = CidrCalculator.calculate("192.168.1.2/31");
        assertEquals("192.168.1.2/31", info.cidr());
        assertEquals("192.168.1.2", info.networkAddress());
        assertEquals("192.168.1.3", info.broadcastAddress());
        assertEquals("255.255.255.254", info.netmask());
        assertEquals("192.168.1.2", info.firstHost());
        assertEquals("192.168.1.3", info.lastHost());
        assertEquals(2L, info.totalHosts());
        assertEquals(2L, info.usableHosts());
        assertEquals(31, info.prefixLength());
    }

    @Test
    @DisplayName("Calculate edge /0 whole Internet address space")
    void testSlash0AllInternet() {
        SubnetInfo info = CidrCalculator.calculate("0.0.0.0/0");
        assertEquals("0.0.0.0/0", info.cidr());
        assertEquals("0.0.0.0", info.networkAddress());
        assertEquals("255.255.255.255", info.broadcastAddress());
        assertEquals("0.0.0.0", info.netmask());
        assertEquals("0.0.0.1", info.firstHost());
        assertEquals("255.255.255.254", info.lastHost());
        assertEquals(4294967296L, info.totalHosts());
        assertEquals(4294967294L, info.usableHosts());
        assertEquals(0, info.prefixLength());
    }

    @ParameterizedTest(name = "contains({0}, {1}) -> {2}")
    @CsvSource({
            "192.168.1.0/24, 192.168.1.1, true",
            "192.168.1.0/24, 192.168.1.254, true",
            "192.168.1.0/24, 192.168.1.0, true",
            "192.168.1.0/24, 192.168.1.255, true",
            "192.168.1.0/24, 192.168.2.1, false",
            "10.0.0.0/8, 10.123.45.67, true",
            "10.0.0.0/8, 11.0.0.1, false",
            "0.0.0.0/0, 1.2.3.4, true",
            "0.0.0.0/0, 203.0.113.195, true",
            "192.168.1.10/32, 192.168.1.10, true",
            "192.168.1.10/32, 192.168.1.11, false"
    })
    @DisplayName("Verify CIDR containment checks")
    void testContains(String cidr, String ip, boolean expected) {
        assertEquals(expected, CidrCalculator.contains(cidr, ip));
    }

    @Test
    @DisplayName("Verify CIDR subnet overlap detection")
    void testOverlaps() {
        // Enclosing supernet / subnet
        assertTrue(CidrCalculator.overlaps("10.0.0.0/8", "10.1.0.0/16"));
        assertTrue(CidrCalculator.overlaps("10.1.0.0/16", "10.0.0.0/8"));

        // Exact match
        assertTrue(CidrCalculator.overlaps("192.168.1.0/24", "192.168.1.0/24"));

        // Subnet inside subnet
        assertTrue(CidrCalculator.overlaps("192.168.1.0/24", "192.168.1.128/25"));

        // Adjacent but disjoint
        assertFalse(CidrCalculator.overlaps("192.168.1.0/25", "192.168.1.128/25"));
        assertFalse(CidrCalculator.overlaps("192.168.1.0/24", "192.168.2.0/24"));
        assertFalse(CidrCalculator.overlaps("10.0.0.0/8", "172.16.0.0/12"));
    }

    @Test
    @DisplayName("Invalid CIDR formats and prefixes throw IllegalArgumentException")
    void testInvalidInputs() {
        assertThrows(NullPointerException.class, () -> CidrCalculator.calculate(null));
        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.calculate("192.168.1.1"));
        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.calculate("192.168.1.1/33"));
        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.calculate("192.168.1.1/-1"));
        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.calculate("192.168.1.1/abc"));
        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.calculate("256.0.0.1/24"));
        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.calculate("1.2.3.4.5/24"));
        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.calculate("1.2.3/24"));

        assertThrows(NullPointerException.class, () -> CidrCalculator.contains(null, "1.1.1.1"));
        assertThrows(NullPointerException.class, () -> CidrCalculator.contains("1.1.1.0/24", null));
        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.contains("invalid", "1.1.1.1"));
    }

    @Test
    @DisplayName("ipToLong and longToIp conversions round-trip")
    void testIpConversionRoundTrip() {
        String original = "192.168.100.254";
        long numeric = CidrCalculator.ipToLong(original);
        String converted = CidrCalculator.longToIp(numeric);
        assertEquals(original, converted);

        assertEquals(0L, CidrCalculator.ipToLong("0.0.0.0"));
        assertEquals("0.0.0.0", CidrCalculator.longToIp(0L));

        assertEquals(0xFFFFFFFFL, CidrCalculator.ipToLong("255.255.255.255"));
        assertEquals("255.255.255.255", CidrCalculator.longToIp(0xFFFFFFFFL));

        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.longToIp(-1L));
        assertThrows(IllegalArgumentException.class, () -> CidrCalculator.longToIp(0x100000000L));
    }
}
