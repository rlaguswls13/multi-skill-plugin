package com.msp.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Validators Utility Tests")
class ValidatorsTest {

    @Test
    @DisplayName("isBlank and isNotBlank test")
    void testBlank() {
        assertTrue(Validators.isBlank(null));
        assertTrue(Validators.isBlank(""));
        assertTrue(Validators.isBlank("   "));
        assertFalse(Validators.isBlank("abc"));

        assertFalse(Validators.isNotBlank(null));
        assertFalse(Validators.isNotBlank("   "));
        assertTrue(Validators.isNotBlank("hello"));
    }

    @Test
    @DisplayName("Collection isEmpty and isNotEmpty test")
    void testCollectionEmpty() {
        assertTrue(Validators.isEmpty((List<?>) null));
        assertTrue(Validators.isEmpty(List.of()));
        assertFalse(Validators.isEmpty(List.of(1)));

        assertTrue(Validators.isNotEmpty(List.of("a")));
        assertFalse(Validators.isNotEmpty(List.of()));

        assertTrue(Validators.isEmpty((Map<?, ?>) null));
        assertTrue(Validators.isNotEmpty(Map.of("k", "v")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test@example.com",
            "alice.smith+tag@sub.domain.org",
            "user_name-123@domain.co.kr"
    })
    @DisplayName("Valid emails should pass")
    void testValidEmail(String email) {
        assertTrue(Validators.isEmail(email), () -> "Expected valid: " + email);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "plainaddress",
            "@missingusername.com",
            "username@.com",
            "user@domain"
    })
    @DisplayName("Invalid emails should fail")
    void testInvalidEmail(String email) {
        assertFalse(Validators.isEmail(email), () -> "Expected invalid: " + email);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://localhost:8080/api/users",
            "https://sub.domain.org/path?q=1#hash",
            "ftp://files.example.com/pub"
    })
    @DisplayName("Valid URLs should pass")
    void testValidUrl(String url) {
        assertTrue(Validators.isUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "ftp-broken",
            "http//missingcolon.com",
            "httpx://unknownscheme.org",
            "not a url"
    })
    @DisplayName("Invalid URLs should fail")
    void testInvalidUrl(String url) {
        assertFalse(Validators.isUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0",
            "127.0.0.1",
            "192.168.1.1",
            "255.255.255.255"
    })
    @DisplayName("Valid IPv4 should pass")
    void testValidIpv4(String ip) {
        assertTrue(Validators.isIpv4(ip));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "256.0.0.1",
            "192.168.1",
            "192.168.1.1.1",
            "192.168.01.1", // leading zero
            "abc.def.ghi.jkl",
            "",
            "   "
    })
    @DisplayName("Invalid IPv4 should fail")
    void testInvalidIpv4(String ip) {
        assertFalse(Validators.isIpv4(ip));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::",
            "::1",
            "2001:db8::1",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "fe80::1ff:fe23:4567:890a"
    })
    @DisplayName("Valid IPv6 should pass")
    void testValidIpv6(String ip) {
        assertTrue(Validators.isIpv6(ip));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2001:db8:::1", // triple colon
            "2001:db8::1::2", // double ::
            "2001:xyz::1", // non hex
            "127.0.0.1", // ipv4
            ""
    })
    @DisplayName("Invalid IPv6 should fail")
    void testInvalidIpv6(String ip) {
        assertFalse(Validators.isIpv6(ip));
    }

    @Test
    @DisplayName("inRange and hasLength test")
    void testRangeAndLength() {
        assertTrue(Validators.inRange(20, 10, 30));
        assertFalse(Validators.inRange(5, 10, 30));
        assertFalse(Validators.inRange(35, 10, 30));

        assertTrue(Validators.hasLength("hello", 3, 5));
        assertFalse(Validators.hasLength("hello world", 3, 5));
    }
}
