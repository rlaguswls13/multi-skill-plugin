package com.msp.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Production-grade AES-256-GCM authenticated encryption and decryption.
 *
 * <p>Security features:
 * <ul>
 *   <li>Algorithm: AES/GCM/NoPadding (authenticated encryption with 128-bit integrity tag).</li>
 *   <li>12-byte cryptographically secure random IV generated per encryption.</li>
 *   <li>Wire format: {@code [12-byte IV][Ciphertext + 16-byte Auth Tag]}.</li>
 *   <li>Optional Additional Authenticated Data (AAD) support.</li>
 * </ul>
 */
public final class AesGcmCrypto {

    public static final int GCM_IV_LENGTH_BYTES = 12;
    public static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8; // 16 bytes
    public static final int MIN_PAYLOAD_LENGTH_BYTES = GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES; // 28 bytes

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcmCrypto() {
        // Utility class
    }

    /**
     * Generates a new 256-bit AES SecretKey.
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, RANDOM);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("Failed to initialize AES KeyGenerator", e);
        }
    }

    /**
     * Creates an AES SecretKey from raw 32-byte (256-bit) key material.
     */
    public static SecretKey keyFromBytes(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES key must be 128, 192, or 256 bits (16, 24, or 32 bytes)");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Creates an AES SecretKey from a Base64-encoded key string.
     */
    public static SecretKey keyFromBase64(String base64Key) {
        Objects.requireNonNull(base64Key, "base64Key must not be null");
        return keyFromBytes(Base64.getDecoder().decode(base64Key));
    }

    /**
     * Encrypts plaintext bytes with optional AAD.
     * Output format: {@code [12-byte IV][Ciphertext + 16-byte GCM Tag]}.
     */
    public static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] aad) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        Objects.requireNonNull(key, "key must not be null");

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        RANDOM.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            byte[] cipherBytes = cipher.doFinal(plaintext);

            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return combined;
        } catch (Exception e) {
            throw new CryptoException("Failed to encrypt data with AES-GCM", e);
        }
    }

    /**
     * Encrypts plaintext bytes without AAD.
     */
    public static byte[] encrypt(byte[] plaintext, SecretKey key) {
        return encrypt(plaintext, key, null);
    }

    /**
     * Decrypts ciphertext (prepended with 12-byte IV) with optional AAD.
     *
     * @throws CryptoException if authentication tag check fails (tampering or wrong key/AAD).
     */
    public static byte[] decrypt(byte[] ciphertextWithIv, SecretKey key, byte[] aad) {
        Objects.requireNonNull(ciphertextWithIv, "ciphertextWithIv must not be null");
        Objects.requireNonNull(key, "key must not be null");

        if (ciphertextWithIv.length < MIN_PAYLOAD_LENGTH_BYTES) {
            throw new IllegalArgumentException("Ciphertext payload too short. Minimum "
                    + MIN_PAYLOAD_LENGTH_BYTES + " bytes required, got " + ciphertextWithIv.length);
        }

        byte[] iv = Arrays.copyOfRange(ciphertextWithIv, 0, GCM_IV_LENGTH_BYTES);
        byte[] cipherBytes = Arrays.copyOfRange(ciphertextWithIv, GCM_IV_LENGTH_BYTES, ciphertextWithIv.length);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            throw new CryptoException("AES-GCM decryption failed: authentication tag mismatch or invalid payload", e);
        }
    }

    /**
     * Decrypts ciphertext (prepended with 12-byte IV) without AAD.
     */
    public static byte[] decrypt(byte[] ciphertextWithIv, SecretKey key) {
        return decrypt(ciphertextWithIv, key, null);
    }

    /**
     * Encrypts a UTF-8 string to a Base64 string with optional AAD.
     */
    public static String encryptToBase64(String plaintext, SecretKey key, String aad) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] aadBytes = (aad != null) ? aad.getBytes(StandardCharsets.UTF_8) : null;
        byte[] encrypted = encrypt(plaintext.getBytes(StandardCharsets.UTF_8), key, aadBytes);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Encrypts a UTF-8 string to a Base64 string.
     */
    public static String encryptToBase64(String plaintext, SecretKey key) {
        return encryptToBase64(plaintext, key, null);
    }

    /**
     * Decrypts a Base64 ciphertext string back to UTF-8 plaintext with optional AAD.
     */
    public static String decryptFromBase64(String base64Ciphertext, SecretKey key, String aad) {
        Objects.requireNonNull(base64Ciphertext, "base64Ciphertext must not be null");
        byte[] rawBytes = Base64.getDecoder().decode(base64Ciphertext);
        byte[] aadBytes = (aad != null) ? aad.getBytes(StandardCharsets.UTF_8) : null;
        byte[] decrypted = decrypt(rawBytes, key, aadBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * Decrypts a Base64 ciphertext string back to UTF-8 plaintext.
     */
    public static String decryptFromBase64(String base64Ciphertext, SecretKey key) {
        return decryptFromBase64(base64Ciphertext, key, null);
    }
}
