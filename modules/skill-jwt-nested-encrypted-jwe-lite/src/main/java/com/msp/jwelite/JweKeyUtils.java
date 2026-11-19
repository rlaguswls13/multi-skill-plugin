package com.msp.jwelite;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Key generation and Base64URL encoding utilities for JWE operations.
 */
public final class JweKeyUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private JweKeyUtils() {
    }

    public static SecureRandom getSecureRandom() {
        return SECURE_RANDOM;
    }

    public static KeyPair generateRsaKeyPair(int keySize) {
        if (keySize < 2048) {
            throw new IllegalArgumentException("RSA key size should be at least 2048 bits for secure JWE usage");
        }
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(keySize, SECURE_RANDOM);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new JweException("RSA key pair generation failed", e);
        }
    }

    public static SecretKey generateAesKey(int keyBits) {
        if (keyBits != 128 && keyBits != 192 && keyBits != 256) {
            throw new IllegalArgumentException("AES key size must be 128, 192, or 256 bits, got: " + keyBits);
        }
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(keyBits, SECURE_RANDOM);
            return kg.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new JweException("AES key generation failed", e);
        }
    }

    public static SecretKey aesKeyFromBytes(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static String base64UrlEncode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] base64UrlDecode(String base64Url) {
        if (base64Url == null || base64Url.isEmpty()) {
            return new byte[0];
        }
        return Base64.getUrlDecoder().decode(base64Url);
    }
}
