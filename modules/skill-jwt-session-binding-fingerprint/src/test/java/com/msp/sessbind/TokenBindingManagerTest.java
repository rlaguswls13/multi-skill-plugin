package com.msp.sessbind;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenBindingManagerTest {

    private final byte[] secretKey = "test-hmac-secret-super-safe-key-123".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("Should successfully validate identical client connection context")
    void testSuccessfulValidation() {
        ClientFingerprintContext ctx = ClientFingerprintContext.builder()
                .ipAddress("198.51.100.14")
                .ipBindingMode(IpBindingMode.SUBNET)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .tlsCertThumbprint("a1b2c3d4e5f6")
                .deviceKeyId("dev-uuid-999")
                .build();

        Map<String, Object> claims = TokenBindingManager.createBindingClaims(ctx, secretKey);

        TokenBindingValidationResult result = TokenBindingManager.validate(ctx, claims, secretKey);
        assertTrue(result.isValid());
        assertEquals(MismatchType.NONE, result.mismatchType());
        assertEquals(MismatchSeverity.NONE, result.severity());
    }

    @Test
    @DisplayName("Should tolerate IP change within same /24 subnet under SUBNET mode")
    void testSubnetRoamingAllowed() {
        ClientFingerprintContext initialCtx = ClientFingerprintContext.builder()
                .ipAddress("192.168.1.10")
                .ipBindingMode(IpBindingMode.SUBNET)
                .userAgent("Mozilla/5.0")
                .build();

        Map<String, Object> claims = TokenBindingManager.createBindingClaims(initialCtx, secretKey);

        // Client roams from 192.168.1.10 to 192.168.1.88 (same /24)
        ClientFingerprintContext roamingCtx = ClientFingerprintContext.builder()
                .ipAddress("192.168.1.88")
                .ipBindingMode(IpBindingMode.SUBNET)
                .userAgent("Mozilla/5.0")
                .build();

        TokenBindingValidationResult result = TokenBindingManager.validate(roamingCtx, claims, secretKey);
        assertTrue(result.isValid(), "Same /24 subnet should validate under SUBNET mode");
    }

    @Test
    @DisplayName("Should reject IP change within same /24 subnet under EXACT mode")
    void testExactIpMismatch() {
        ClientFingerprintContext initialCtx = ClientFingerprintContext.builder()
                .ipAddress("192.168.1.10")
                .ipBindingMode(IpBindingMode.EXACT)
                .userAgent("Mozilla/5.0")
                .build();

        Map<String, Object> claims = TokenBindingManager.createBindingClaims(initialCtx, secretKey);

        ClientFingerprintContext roamingCtx = ClientFingerprintContext.builder()
                .ipAddress("192.168.1.88")
                .ipBindingMode(IpBindingMode.EXACT)
                .userAgent("Mozilla/5.0")
                .build();

        TokenBindingValidationResult result = TokenBindingManager.validate(roamingCtx, claims, secretKey);
        assertFalse(result.isValid());
        assertEquals(MismatchType.IP_SUBNET_MISMATCH, result.mismatchType());
    }

    @Test
    @DisplayName("Should detect User-Agent mismatch (token theft attempt)")
    void testUserAgentMismatch() {
        ClientFingerprintContext issueCtx = ClientFingerprintContext.builder()
                .ipAddress("203.0.113.1")
                .ipBindingMode(IpBindingMode.SUBNET)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Chrome/120)")
                .build();

        Map<String, Object> claims = TokenBindingManager.createBindingClaims(issueCtx, secretKey);

        // Attacker uses different browser or tool
        ClientFingerprintContext attackerCtx = ClientFingerprintContext.builder()
                .ipAddress("203.0.113.25")
                .ipBindingMode(IpBindingMode.SUBNET)
                .userAgent("curl/8.4.0")
                .build();

        TokenBindingValidationResult result = TokenBindingManager.validate(attackerCtx, claims, secretKey);
        assertFalse(result.isValid());
        assertEquals(MismatchType.USER_AGENT_MISMATCH, result.mismatchType());
        assertEquals(MismatchSeverity.HIGH, result.severity());
    }

    @Test
    @DisplayName("Should detect TLS certificate thumbprint mismatch with CRITICAL severity")
    void testTlsChannelMismatch() {
        ClientFingerprintContext issueCtx = ClientFingerprintContext.builder()
                .ipAddress("198.51.100.2")
                .tlsCertThumbprint("valid-tls-cert-sha256")
                .userAgent("AppClient/1.0")
                .build();

        Map<String, Object> claims = TokenBindingManager.createBindingClaims(issueCtx, secretKey);

        ClientFingerprintContext attackerCtx = ClientFingerprintContext.builder()
                .ipAddress("198.51.100.2")
                .tlsCertThumbprint("attacker-tls-cert-sha256")
                .userAgent("AppClient/1.0")
                .build();

        TokenBindingValidationResult result = TokenBindingManager.validate(attackerCtx, claims, secretKey);
        assertFalse(result.isValid());
        assertEquals(MismatchType.TLS_CHANNEL_MISMATCH, result.mismatchType());
        assertEquals(MismatchSeverity.CRITICAL, result.severity());
    }

    @Test
    @DisplayName("Should reject claims missing 'bnd'")
    void testMissingBindingClaim() {
        ClientFingerprintContext ctx = ClientFingerprintContext.builder()
                .ipAddress("10.0.0.1")
                .userAgent("Agent")
                .build();

        TokenBindingValidationResult result = TokenBindingManager.validate(ctx, Map.of("sub", "user123"), secretKey);
        assertFalse(result.isValid());
        assertEquals(MismatchType.MISSING_BINDING_CLAIM, result.mismatchType());
    }

    @Test
    @DisplayName("IPv6 subnet helper masks out host portion to /64")
    void testIpv6Subnet() {
        String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
        String subnet = IpSubnetHelper.getSubnet(ipv6);
        assertEquals("2001:db8:85a3:0:0:0:0:0/64", subnet);
    }
}
