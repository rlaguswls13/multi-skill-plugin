package com.msp.ipasn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IPv4 Country & ASN Fast Lookup Probe Tests")
class IpAsnLookupEngineTest {

    private IpAsnLookupEngine engine;

    @BeforeEach
    void setUp() {
        engine = new IpAsnLookupEngine();
    }

    @Test
    @DisplayName("Range registration and exact/boundary lookups")
    void testRangeLookup() {
        // KT (Korea Telecom) sample block: 211.234.0.0 - 211.234.255.255
        engine.registerRange("211.234.0.0", "211.234.255.255", "KR", 4766, "Korea Telecom");

        assertEquals(1, engine.size());

        // Boundary: start IP
        Optional<IpGeoAsnRecord> start = engine.lookup("211.234.0.0");
        assertTrue(start.isPresent());
        assertEquals("KR", start.get().countryCode());
        assertEquals(4766, start.get().asn());
        assertEquals("Korea Telecom", start.get().asName());

        // Boundary: end IP
        Optional<IpGeoAsnRecord> end = engine.lookup("211.234.255.255");
        assertTrue(end.isPresent());
        assertEquals("KR", end.get().countryCode());

        // Mid-range
        Optional<IpGeoAsnRecord> mid = engine.lookup("211.234.128.45");
        assertTrue(mid.isPresent());
        assertEquals("KR", mid.get().countryCode());

        // Outside range (before & after)
        assertFalse(engine.lookup("211.233.255.255").isPresent());
        assertFalse(engine.lookup("211.235.0.0").isPresent());
    }

    @Test
    @DisplayName("CIDR notation registration and resolution")
    void testCidrLookup() {
        // Cloudflare 1.1.1.0/24
        engine.registerCidr("1.1.1.0/24", "US", 13335, "Cloudflare, Inc.");
        // Google DNS 8.8.8.0/24
        engine.registerCidr("8.8.8.0/24", "US", 15169, "Google LLC");

        assertEquals(2, engine.size());

        Optional<IpGeoAsnRecord> cf = engine.lookup("1.1.1.1");
        assertTrue(cf.isPresent());
        assertEquals("US", cf.get().countryCode());
        assertEquals(13335, cf.get().asn());
        assertEquals("Cloudflare, Inc.", cf.get().asName());

        Optional<IpGeoAsnRecord> google = engine.lookup("8.8.8.8");
        assertTrue(google.isPresent());
        assertEquals(15169, google.get().asn());

        // Outside CIDR block
        assertFalse(engine.lookup("1.1.2.1").isPresent());
    }

    @Test
    @DisplayName("Nested overlapping CIDRs resolve to most specific match (longest prefix)")
    void testNestedSubnetsLongestPrefixMatch() {
        // Large block: 10.0.0.0/8
        engine.registerCidr("10.0.0.0/8", "US", 1000, "Enterprise Parent");
        // Smaller nested subnet: 10.1.0.0/16
        engine.registerCidr("10.1.0.0/16", "KR", 2000, "Korea Subnet");
        // Highly specific single host /32: 10.1.5.9/32
        engine.registerCidr("10.1.5.9/32", "JP", 3000, "Specific Node");

        // 10.1.5.9 should match /32
        Optional<IpGeoAsnRecord> node = engine.lookup("10.1.5.9");
        assertTrue(node.isPresent());
        assertEquals(3000, node.get().asn());
        assertEquals("JP", node.get().countryCode());

        // 10.1.5.10 should fall back to /16
        Optional<IpGeoAsnRecord> kr = engine.lookup("10.1.5.10");
        assertTrue(kr.isPresent());
        assertEquals(2000, kr.get().asn());
        assertEquals("KR", kr.get().countryCode());

        // 10.2.0.1 should fall back to /8
        Optional<IpGeoAsnRecord> parent = engine.lookup("10.2.0.1");
        assertTrue(parent.isPresent());
        assertEquals(1000, parent.get().asn());
        assertEquals("US", parent.get().countryCode());
    }

    @Test
    @DisplayName("IP to 32-bit unsigned Long parsing and conversion roundtrip")
    void testIpLongConversion() {
        assertEquals(0L, IpAsnLookupEngine.parseIpv4ToLong("0.0.0.0"));
        assertEquals("0.0.0.0", IpAsnLookupEngine.longToIpv4(0L));

        assertEquals(0xFFFFFFFFL, IpAsnLookupEngine.parseIpv4ToLong("255.255.255.255"));
        assertEquals("255.255.255.255", IpAsnLookupEngine.longToIpv4(0xFFFFFFFFL));

        String ip = "192.168.1.100";
        long val = IpAsnLookupEngine.parseIpv4ToLong(ip);
        assertEquals(ip, IpAsnLookupEngine.longToIpv4(val));
    }

    @Test
    @DisplayName("Invalid IP and CIDR formats throw IllegalArgumentException")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> engine.lookup(null));
        assertThrows(IllegalArgumentException.class, () -> engine.lookup(""));
        assertThrows(IllegalArgumentException.class, () -> engine.lookup("192.168.1"));
        assertThrows(IllegalArgumentException.class, () -> engine.lookup("192.168.1.1.1"));
        assertThrows(IllegalArgumentException.class, () -> engine.lookup("256.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> engine.lookup("192.168.01.1")); // leading zero
        assertThrows(IllegalArgumentException.class, () -> engine.lookup("abc.def.ghi.jkl"));

        // Range inverted
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerRange("192.168.1.100", "192.168.1.10", "KR", 100, "Test"));

        // CIDR invalid prefix
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerCidr("192.168.1.0/33", "KR", 100, "Test"));
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerCidr("192.168.1.0/-1", "KR", 100, "Test"));
    }

    @Test
    @DisplayName("clear() resets lookup engine")
    void testClear() {
        engine.registerCidr("8.8.8.0/24", "US", 15169, "Google");
        assertEquals(1, engine.size());

        engine.clear();
        assertEquals(0, engine.size());
        assertFalse(engine.lookup("8.8.8.8").isPresent());
    }
}
