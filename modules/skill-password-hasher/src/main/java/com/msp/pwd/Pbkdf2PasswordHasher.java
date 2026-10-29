package com.msp.pwd;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;

/**
 * Robust password hashing implementation based on PBKDF2WithHmacSHA256.
 *
 * <p>Format: {@code $pbkdf2$<iterations>$<salt_base64>$<hash_base64>}
 *
 * <p>Defaults:
 * <ul>
 *   <li>Algorithm: PBKDF2WithHmacSHA256</li>
 *   <li>Iterations: 65,536 (OWASP recommended minimum work factor)</li>
 *   <li>Salt: 16 bytes (128 bits) cryptographically secure random</li>
 *   <li>Derived Hash: 32 bytes (256 bits)</li>
 *   <li>Constant-time comparison using {@link MessageDigest#isEqual(byte[], byte[])}</li>
 * </ul>
 */
public final class Pbkdf2PasswordHasher implements PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final String PREFIX = "$pbkdf2$";
    public static final int DEFAULT_ITERATIONS = 65_536;
    public static final int DEFAULT_SALT_BYTES = 16;
    public static final int DEFAULT_KEY_LENGTH_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final int iterations;
    private final int saltBytes;
    private final int keyLengthBits;

    public Pbkdf2PasswordHasher() {
        this(DEFAULT_ITERATIONS, DEFAULT_SALT_BYTES, DEFAULT_KEY_LENGTH_BITS);
    }

    public Pbkdf2PasswordHasher(int iterations) {
        this(iterations, DEFAULT_SALT_BYTES, DEFAULT_KEY_LENGTH_BITS);
    }

    public Pbkdf2PasswordHasher(int iterations, int saltBytes, int keyLengthBits) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive: " + iterations);
        }
        if (saltBytes <= 0) {
            throw new IllegalArgumentException("saltBytes must be positive: " + saltBytes);
        }
        if (keyLengthBits <= 0) {
            throw new IllegalArgumentException("keyLengthBits must be positive: " + keyLengthBits);
        }
        this.iterations = iterations;
        this.saltBytes = saltBytes;
        this.keyLengthBits = keyLengthBits;
    }

    @Override
    public String hash(CharSequence rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");

        byte[] salt = new byte[saltBytes];
        RANDOM.nextBytes(salt);

        byte[] hashBytes = pbkdf2(toCharArray(rawPassword), salt, iterations, keyLengthBits);

        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return PREFIX
                + iterations + "$"
                + encoder.encodeToString(salt) + "$"
                + encoder.encodeToString(hashBytes);
    }

    @Override
    public boolean verify(CharSequence rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null || !encodedHash.startsWith(PREFIX)) {
            return false;
        }

        try {
            String[] parts = encodedHash.split("\\$");
            // Encoded format: "" / "pbkdf2" / "<iterations>" / "<salt>" / "<hash>"
            if (parts.length != 5 || !parts[1].equals("pbkdf2")) {
                return false;
            }

            int hashIterations = Integer.parseInt(parts[2]);
            if (hashIterations <= 0) {
                return false;
            }

            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[3]);
            byte[] expectedHash = decoder.decode(parts[4]);

            byte[] actualHash = pbkdf2(toCharArray(rawPassword), salt, hashIterations, expectedHash.length * 8);

            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to calculate PBKDF2 hash", e);
        }
    }

    private static char[] toCharArray(CharSequence seq) {
        if (seq instanceof String s) {
            return s.toCharArray();
        }
        char[] chars = new char[seq.length()];
        for (int i = 0; i < seq.length(); i++) {
            chars[i] = seq.charAt(i);
        }
        return chars;
    }
}
