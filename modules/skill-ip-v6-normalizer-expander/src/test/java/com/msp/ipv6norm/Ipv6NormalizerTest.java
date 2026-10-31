package com.msp.ipv6norm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class Ipv6NormalizerTest {

    @Test
    @DisplayName("Expand fully expands IPv6 into 39-character lowercase 8-block format")
    void testExpand() {
        assertEquals("0000:0000:0000:0000:0000:0000:0000:0000", Ipv6Normalizer.expand("::"));
        assertEquals("0000:0000:0000:0000:0000:0000:0000:0001", Ipv6Normalizer.expand("::1"));
        assertEquals("2001:0db8:0000:0000:0000:ff00:0042:8329", Ipv6Normalizer.expand("2001:db8::ff00:42:8329"));
        assertEquals("fe80:0000:0000:0000:0000:0000:0000:0001", Ipv6Normalizer.expand("[fe80::1%eth0]"));
    }

    @Test
    @DisplayName("RFC 5952 standard compression compliance")
    void testCompressRfc5952() {
        // 1. All zeros
        assertEquals("::", Ipv6Normalizer.compress("0000:0000:0000:0000:0000:0000:0000:0000"));
        assertEquals("::", Ipv6Normalizer.compress("::"));

        // 2. Loopback
        assertEquals("::1", Ipv6Normalizer.compress("0:0:0:0:0:0:0:1"));

        // 3. Leading zeros suppressed
        assertEquals("2001:db8::ff00:42:8329", Ipv6Normalizer.compress("2001:0db8:0000:0000:0000:ff00:0042:8329"));

        // 4. RFC 5952 4.2.2: Single 16-bit 0 field MUST NOT be compressed
        assertEquals("2001:db8:0:1:1:1:1:1", Ipv6Normalizer.compress("2001:0db8:0000:0001:0001:0001:0001:0001"));

        // 5. RFC 5952 4.2.3: In case of equal-length ties, the first longest run is shortened
        assertEquals("2001::1:0:0:1:1", Ipv6Normalizer.compress("2001:0:0:1:0:0:1:1"));

        // 6. Lowercase hexadecimal formatting
        assertEquals("2001:db8::abcd", Ipv6Normalizer.compress("2001:DB8:0000:0000:0000:0000:0000:ABCD"));
    }

    @Test
    @DisplayName("IPv4-mapped IPv6 detection and extraction")
    void testIpv4Mapped() {
        String mappedDotted = "::ffff:192.168.1.100";
        String mappedHex = "0000:0000:0000:0000:0000:ffff:c0a8:0164";

        assertTrue(Ipv6Normalizer.isIpv4Mapped(mappedDotted));
        assertTrue(Ipv6Normalizer.isIpv4Mapped(mappedHex));

        assertEquals("192.168.1.100", Ipv6Normalizer.extractIpv4(mappedDotted));
        assertEquals("192.168.1.100", Ipv6Normalizer.extractIpv4(mappedHex));

        assertEquals(Optional.of("192.168.1.100"), Ipv6Normalizer.findIpv4(mappedDotted));

        // Non-mapped IPv6 address
        String regular = "2001:db8::1";
        assertFalse(Ipv6Normalizer.isIpv4Mapped(regular));
        assertEquals(Optional.empty(), Ipv6Normalizer.findIpv4(regular));
        assertThrows(IllegalArgumentException.class, () -> Ipv6Normalizer.extractIpv4(regular));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::",
            "::1",
            "2001:db8::",
            "fe80::1ff:fe23:4567:890a",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "::ffff:192.0.2.1",
            "[2001:db8::1]",
            "fe80::1%1"
    })
    @DisplayName("isValid returns true for valid IPv6 variants")
    void testValidAddresses(String ip) {
        assertTrue(Ipv6Normalizer.isValid(ip), "Should be valid: " + ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "127.0.0.1",
            "2001:db8:::1",
            "2001:db8::1::2",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334:extra",
            "2001:zzzz::1",
            "::ffff:256.0.0.1",
            "::ffff:192.168.01.1",
            "2001:12345::1"
    })
    @DisplayName("isValid returns false and expand throws for invalid addresses")
    void testInvalidAddresses(String ip) {
        assertFalse(Ipv6Normalizer.isValid(ip), "Should be invalid: " + ip);
        assertThrows(IllegalArgumentException.class, () -> Ipv6Normalizer.expand(ip));
        assertThrows(IllegalArgumentException.class, () -> Ipv6Normalizer.compress(ip));
    }

    @Test
    @DisplayName("Null handling")
    void testNullHandling() {
        assertFalse(Ipv6Normalizer.isValid(null));
        assertThrows(IllegalArgumentException.class, () -> Ipv6Normalizer.expand(null));
        assertThrows(IllegalArgumentException.class, () -> Ipv6Normalizer.compress(null));
    }
}
