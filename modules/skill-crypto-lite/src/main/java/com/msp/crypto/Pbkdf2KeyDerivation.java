package com.msp.crypto;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;

/**
 * PBKDF2WithHmacSHA256 key derivation utility.
 *
 * <p>Converts human passwords or passphrases into cryptographically strong cryptographic keys.
 * Default settings: 60,000 iterations, 256-bit key length, 16-byte random salt.
 */
public final class Pbkdf2KeyDerivation {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ITERATIONS = 60_000;
    public static final int DEFAULT_KEY_LENGTH_BITS = 256;
    public static final int DEFAULT_SALT_LENGTH_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Pbkdf2KeyDerivation() {
        // Utility class
    }

    /**
     * Generates a cryptographically secure random salt of the specified length.
     */
    public static byte[] generateSalt(int lengthBytes) {
        if (lengthBytes <= 0) {
            throw new IllegalArgumentException("Salt length must be positive: " + lengthBytes);
        }
        byte[] salt = new byte[lengthBytes];
        RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * Generates a default 16-byte cryptographically secure random salt.
     */
    public static byte[] generateSalt() {
        return generateSalt(DEFAULT_SALT_LENGTH_BYTES);
    }

    /**
     * Derives raw key bytes using PBKDF2WithHmacSHA256.
     */
    public static byte[] deriveKey(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(salt, "salt must not be null");

        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive: " + iterations);
        }
        if (keyLengthBits <= 0) {
            throw new IllegalArgumentException("keyLengthBits must be positive: " + keyLengthBits);
        }

        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new CryptoException("Failed to derive key with PBKDF2", e);
        }
    }

    /**
     * Derives an AES SecretKey with custom iterations and key length.
     */
    public static SecretKey deriveAesKey(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        byte[] keyBytes = deriveKey(password, salt, iterations, keyLengthBits);
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Derives a 256-bit AES SecretKey using default 60,000 iterations.
     */
    public static SecretKey deriveAesKey(char[] password, byte[] salt) {
        return deriveAesKey(password, salt, DEFAULT_ITERATIONS, DEFAULT_KEY_LENGTH_BITS);
    }

    /**
     * Derives a 256-bit AES SecretKey from a String password using default 60,000 iterations.
     */
    public static SecretKey deriveAesKey(String password, byte[] salt) {
        Objects.requireNonNull(password, "password must not be null");
        return deriveAesKey(password.toCharArray(), salt, DEFAULT_ITERATIONS, DEFAULT_KEY_LENGTH_BITS);
    }
}
