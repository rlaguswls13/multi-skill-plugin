package com.msp.starter.security;

import com.msp.crypto.CryptoLite;
import com.msp.jwt.JwtLite;
import com.msp.jwt.JwtToken;
import com.msp.pwd.PasswordUtil;
import com.msp.sanitizer.HtmlSanitizer;
import com.msp.sanitizer.TextSanitizers;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 보안, 암호화, 패스워드 해싱, JWT 및 XSS/PII 정제를 통합 제공하는 종합 스타터 파사드.
 */
public final class SecurityAuthKit {

    private SecurityAuthKit() {}

    // 1. Password Hashing
    public static String hashPassword(String rawPassword) {
        return PasswordUtil.hash(rawPassword);
    }

    public static boolean verifyPassword(String rawPassword, String hashedPassword) {
        return PasswordUtil.verify(rawPassword, hashedPassword);
    }

    // 2. AES-256-GCM Encryption
    public static SecretKey generateAesKey() {
        return CryptoLite.generateAesKey();
    }

    public static String encryptAes(String plainText, SecretKey key) {
        return CryptoLite.encryptAesGcm(plainText, key);
    }

    public static String decryptAes(String cipherText, SecretKey key) {
        byte[] decrypted = CryptoLite.decryptAesGcm(java.util.Base64.getDecoder().decode(cipherText), key);
        return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
    }

    // 3. JWT Lite
    public static String createJwt(String subject, Map<String, Object> claims, Duration ttl, String secret) {
        var builder = JwtLite.builder().subject(subject);
        if (claims != null) {
            claims.forEach(builder::claim);
        }
        if (ttl != null) {
            builder.expiresAt(Instant.now().plus(ttl));
        }
        return builder.signWith(JwtLite.hs256(secret)).compact();
    }

    public static boolean verifyJwt(String token, String secret) {
        try {
            JwtToken parsed = JwtLite.parser().verifyWith(JwtLite.hs256(secret)).parse(token);
            return parsed != null;
        } catch (Exception e) {
            return false;
        }
    }

    // 4. Sanitization & Masking
    public static String escapeHtml(String dirtyHtml) {
        return HtmlSanitizer.escapeHtml(dirtyHtml);
    }

    public static String maskPii(String text) {
        return TextSanitizers.maskAllPii(text);
    }
}
