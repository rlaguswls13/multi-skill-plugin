package com.msp.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * High-performance HMAC-SHA256 signature generation and constant-time verification.
 */
public final class HmacSigner {

    public static final String ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private HmacSigner() {
        // Utility class
    }

    /**
     * Signs data using HMAC-SHA256 with the given key bytes.
     */
    public static byte[] sign(byte[] data, byte[] key) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(key, "key must not be null");

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new CryptoException("Failed to generate HMAC signature", e);
        }
    }

    /**
     * Signs data and returns a lowercase hexadecimal signature.
     */
    public static String signHex(byte[] data, byte[] key) {
        return HEX.formatHex(sign(data, key));
    }

    /**
     * Signs UTF-8 string data using a UTF-8 string key and returns hex.
     */
    public static String signHex(String data, String key) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return signHex(data.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Signs data and returns a standard Base64 signature.
     */
    public static String signBase64(byte[] data, byte[] key) {
        return Base64.getEncoder().encodeToString(sign(data, key));
    }

    /**
     * Signs data and returns a URL-safe Base64 signature without padding.
     */
    public static String signBase64Url(byte[] data, byte[] key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sign(data, key));
    }

    /**
     * Verifies an HMAC-SHA256 signature using constant-time comparison (prevents timing attacks).
     */
    public static boolean verify(byte[] data, byte[] signature, byte[] key) {
        if (data == null || signature == null || key == null) {
            return false;
        }
        try {
            byte[] expected = sign(data, key);
            return MessageDigest.isEqual(expected, signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies a hexadecimal HMAC-SHA256 signature against UTF-8 string data.
     */
    public static boolean verifyHex(String data, String signatureHex, String key) {
        if (data == null || signatureHex == null || key == null) {
            return false;
        }
        try {
            byte[] sigBytes = HEX.parseHex(signatureHex);
            return verify(data.getBytes(StandardCharsets.UTF_8), sigBytes, key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies a Base64 HMAC-SHA256 signature against UTF-8 string data.
     */
    public static boolean verifyBase64(String data, String signatureBase64, String key) {
        if (data == null || signatureBase64 == null || key == null) {
            return false;
        }
        try {
            byte[] sigBytes = Base64.getDecoder().decode(signatureBase64);
            return verify(data.getBytes(StandardCharsets.UTF_8), sigBytes, key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
