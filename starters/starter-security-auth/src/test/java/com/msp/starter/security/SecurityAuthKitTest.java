package com.msp.starter.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAuthKitTest {

    @Test
    @DisplayName("Integrated security kit facade test")
    void testSecurityAuthKit() {
        // Password
        String hash = SecurityAuthKit.hashPassword("mySecret123");
        assertTrue(SecurityAuthKit.verifyPassword("mySecret123", hash));
        assertFalse(SecurityAuthKit.verifyPassword("wrongPass", hash));

        // AES
        SecretKey key = SecurityAuthKit.generateAesKey();
        String cipher = SecurityAuthKit.encryptAes("sensitiveData", key);
        assertEquals("sensitiveData", SecurityAuthKit.decryptAes(cipher, key));

        // JWT
        String secret = "01234567890123456789012345678901";
        String token = SecurityAuthKit.createJwt("user1", Map.of("role", "admin"), Duration.ofMinutes(10), secret);
        assertTrue(SecurityAuthKit.verifyJwt(token, secret));
        assertFalse(SecurityAuthKit.verifyJwt(token, "wrongsecretwrongsecretwrongsecret1"));

        // Sanitizer
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", SecurityAuthKit.escapeHtml("<script>alert(1)</script>"));
        assertTrue(SecurityAuthKit.maskPii("010-1234-5678").contains("****"));
    }
}
