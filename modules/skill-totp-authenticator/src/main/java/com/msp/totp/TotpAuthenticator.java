package com.msp.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;

/**
 * RFC 6238 compliant Time-based One-Time Password (TOTP) generator and validator.
 * Built with pure Java 21 standard library.
 */
public final class TotpAuthenticator {

    public static final int DEFAULT_TIME_STEP_SECONDS = 30;
    public static final int DEFAULT_DIGITS = 6;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TotpAuthenticator() {
    }

    /**
     * Generates a cryptographically secure random Base32 secret key.
     *
     * @param byteLength raw key length in bytes (e.g. 20 bytes for 160 bits HMAC-SHA1)
     * @return Base32 encoded secret key
     */
    public static String generateSecretKey(int byteLength) {
        if (byteLength <= 0) {
            throw new IllegalArgumentException("byteLength must be positive: " + byteLength);
        }
        byte[] buffer = new byte[byteLength];
        SECURE_RANDOM.nextBytes(buffer);
        return Base32.encode(buffer);
    }

    /**
     * Generates a standard 20-byte (160-bit) Base32 secret key.
     *
     * @return Base32 encoded secret key
     */
    public static String generateSecretKey() {
        return generateSecretKey(20);
    }

    /**
     * Generates a TOTP code for the given instant and number of digits.
     *
     * @param base32Secret Base32 encoded secret key
     * @param instant      time instant for code generation
     * @param digits       number of output digits (typically 6 or 8)
     * @return padded OTP string
     */
    public static String generateCode(String base32Secret, Instant instant, int digits) {
        Objects.requireNonNull(base32Secret, "base32Secret must not be null");
        Objects.requireNonNull(instant, "instant must not be null");
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("digits must be between 6 and 8: " + digits);
        }
        long step = instant.getEpochSecond() / DEFAULT_TIME_STEP_SECONDS;
        return generateCodeForStep(base32Secret, step, digits);
    }

    /**
     * Generates a 6-digit TOTP code for the given instant.
     */
    public static String generateCode(String base32Secret, Instant instant) {
        return generateCode(base32Secret, instant, DEFAULT_DIGITS);
    }

    /**
     * Generates a 6-digit TOTP code for the current instant.
     */
    public static String generateCurrentCode(String base32Secret) {
        return generateCode(base32Secret, Instant.now(), DEFAULT_DIGITS);
    }

    /**
     * Verifies whether the provided TOTP code is valid within the tolerance window.
     *
     * @param base32Secret Base32 encoded secret key
     * @param code         user provided OTP code string
     * @param instant      reference time instant
     * @param windowSteps  allowed time step difference (e.g. 1 means [-30s, +30s])
     * @return true if code matches within the window, false otherwise
     */
    public static boolean verifyCode(String base32Secret, String code, Instant instant, int windowSteps) {
        if (base32Secret == null || code == null || instant == null) {
            return false;
        }
        if (windowSteps < 0) {
            throw new IllegalArgumentException("windowSteps cannot be negative: " + windowSteps);
        }
        String cleanCode = code.trim();
        int digits = cleanCode.length();
        if (digits < 6 || digits > 8) {
            return false;
        }

        long currentStep = instant.getEpochSecond() / DEFAULT_TIME_STEP_SECONDS;
        boolean matched = false;

        for (int i = -windowSteps; i <= windowSteps; i++) {
            long step = currentStep + i;
            String expected = generateCodeForStep(base32Secret, step, digits);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), cleanCode.getBytes(StandardCharsets.UTF_8))) {
                matched = true;
            }
        }

        return matched;
    }

    /**
     * Verifies code using default windowSteps=1 and current instant.
     */
    public static boolean verifyCode(String base32Secret, String code) {
        return verifyCode(base32Secret, code, Instant.now(), 1);
    }

    /**
     * Generates standard RFC otpauth URI for QR code integration with Google Authenticator.
     *
     * @param issuer       issuer name (e.g. "MyApp")
     * @param accountName  user identifier (e.g. "user@example.com")
     * @param base32Secret Base32 encoded secret key
     * @return standard otpauth://totp URI
     */
    public static String getOtpAuthUrl(String issuer, String accountName, String base32Secret) {
        Objects.requireNonNull(issuer, "issuer must not be null");
        Objects.requireNonNull(accountName, "accountName must not be null");
        Objects.requireNonNull(base32Secret, "base32Secret must not be null");

        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedAccount = URLEncoder.encode(accountName, StandardCharsets.UTF_8).replace("+", "%20");
        String label = encodedIssuer + ":" + encodedAccount;

        return "otpauth://totp/" + label
                + "?secret=" + base32Secret.replace("=", "").trim()
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=6&period=" + DEFAULT_TIME_STEP_SECONDS;
    }

    private static String generateCodeForStep(String base32Secret, long step, int digits) {
        byte[] keyBytes = Base32.decode(base32Secret);
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(step).array();

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int mod = switch (digits) {
                case 6 -> 1_000_000;
                case 7 -> 10_000_000;
                case 8 -> 100_000_000;
                default -> (int) Math.pow(10, digits);
            };

            int otp = binary % mod;
            return String.format("%0" + digits + "d", otp);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA1 for TOTP", e);
        }
    }
}
