package com.msp.totp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TotpAuthenticatorTest {

    // RFC 6238 Appendix B test secret: "12345678901234567890" in ASCII
    // In Base32: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
    private static final String RFC_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    @DisplayName("RFC 4648 Base32 test vectors")
    void testBase32Vectors() {
        assertEquals("", Base32.encode(new byte[0]));
        assertEquals("", new String(Base32.decode(""), StandardCharsets.UTF_8));

        assertEquals("MY======", Base32.encode("f".getBytes(StandardCharsets.UTF_8), true));
        assertEquals("f", new String(Base32.decode("MY======"), StandardCharsets.UTF_8));
        assertEquals("f", new String(Base32.decode("MY"), StandardCharsets.UTF_8));

        assertEquals("MZXW6YTB", Base32.encode("fooba".getBytes(StandardCharsets.UTF_8)));
        assertEquals("fooba", new String(Base32.decode("MZXW6YTB"), StandardCharsets.UTF_8));

        assertEquals("MZXW6YTBOI======", Base32.encode("foobar".getBytes(StandardCharsets.UTF_8), true));
        assertEquals("foobar", new String(Base32.decode("MZXW6YTBOI======"), StandardCharsets.UTF_8));
        assertEquals("foobar", new String(Base32.decode("mzxw6ytboi"), StandardCharsets.UTF_8));
        assertEquals("foobar", new String(Base32.decode("MZXW-6YTBOI "), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Base32 throws on invalid characters")
    void testBase32InvalidCharacter() {
        assertThrows(IllegalArgumentException.class, () -> Base32.decode("MY8901")); // 8 and 9 are invalid in RFC 4648 Base32
    }

    @ParameterizedTest(name = "RFC 6238 vector: time={0}s -> 8-digit OTP={1}, 6-digit OTP={2}")
    @CsvSource({
            "59, 94287082, 287082",
            "1111111109, 07081804, 081804",
            "1111111111, 14050471, 050471",
            "1234567890, 89005924, 005924",
            "2000000000, 69279037, 279037"
    })
    void testRfc6238Vectors(long epochSeconds, String expected8Digits, String expected6Digits) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);

        String code8 = TotpAuthenticator.generateCode(RFC_SECRET_BASE32, instant, 8);
        assertEquals(expected8Digits, code8, "8-digit OTP mismatch for epoch: " + epochSeconds);

        String code6 = TotpAuthenticator.generateCode(RFC_SECRET_BASE32, instant, 6);
        assertEquals(expected6Digits, code6, "6-digit OTP mismatch for epoch: " + epochSeconds);
    }

    @Test
    @DisplayName("Verify code within window")
    void testVerifyCodeWindow() {
        Instant now = Instant.ofEpochSecond(1234567890L);
        String currentCode = TotpAuthenticator.generateCode(RFC_SECRET_BASE32, now, 6);
        assertEquals("005924", currentCode);

        // Current time matches
        assertTrue(TotpAuthenticator.verifyCode(RFC_SECRET_BASE32, currentCode, now, 0));
        assertTrue(TotpAuthenticator.verifyCode(RFC_SECRET_BASE32, currentCode, now, 1));

        // 30 seconds earlier (window=1 should accept, window=0 should reject)
        Instant futureNow = now.plusSeconds(30);
        assertTrue(TotpAuthenticator.verifyCode(RFC_SECRET_BASE32, currentCode, futureNow, 1));
        assertFalse(TotpAuthenticator.verifyCode(RFC_SECRET_BASE32, currentCode, futureNow, 0));

        // 60 seconds earlier (outside window=1)
        Instant farFuture = now.plusSeconds(60);
        assertFalse(TotpAuthenticator.verifyCode(RFC_SECRET_BASE32, currentCode, farFuture, 1));
        assertTrue(TotpAuthenticator.verifyCode(RFC_SECRET_BASE32, currentCode, farFuture, 2));

        // Wrong code
        assertFalse(TotpAuthenticator.verifyCode(RFC_SECRET_BASE32, "999999", now, 1));
        assertFalse(TotpAuthenticator.verifyCode(RFC_SECRET_BASE32, null, now, 1));
        assertFalse(TotpAuthenticator.verifyCode(null, currentCode, now, 1));
    }

    @Test
    @DisplayName("Generate secure random secret key")
    void testGenerateSecretKey() {
        String secret20 = TotpAuthenticator.generateSecretKey(20);
        assertNotNull(secret20);
        byte[] decoded = Base32.decode(secret20);
        assertEquals(20, decoded.length);

        String defaultSecret = TotpAuthenticator.generateSecretKey();
        assertEquals(20, Base32.decode(defaultSecret).length);

        assertThrows(IllegalArgumentException.class, () -> TotpAuthenticator.generateSecretKey(0));
    }

    @Test
    @DisplayName("Generate otpauth URI")
    void testGetOtpAuthUrl() {
        String uri = TotpAuthenticator.getOtpAuthUrl("Acme Corp", "alice@example.com", RFC_SECRET_BASE32);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=" + RFC_SECRET_BASE32));
        assertTrue(uri.contains("issuer=Acme%20Corp"));
        assertTrue(uri.contains("alice%40example.com"));
        assertTrue(uri.contains("algorithm=SHA1"));
        assertTrue(uri.contains("period=30"));
    }
}
