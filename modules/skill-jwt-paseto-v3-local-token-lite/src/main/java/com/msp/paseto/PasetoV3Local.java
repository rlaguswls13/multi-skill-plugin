package com.msp.paseto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Thread-safe **PASETO v3.local** authenticated symmetric encryption engine.
 *
 * <p>Implements the PASETO v3.local specification:
 * <ul>
 *   <li>Header: {@code v3.local.}</li>
 *   <li>Key splitting: HKDF-SHA384 deriving 32-byte {@code Ek}, 16-byte counter nonce {@code n2}, and 48-byte {@code Ak}</li>
 *   <li>Encryption: AES-256-CTR ({@code AES/CTR/NoPadding})</li>
 *   <li>Authentication: HMAC-SHA384 over {@code PAE(header, nonce, ciphertext, footer, implicitAssertion)}
 *       with constant-time verification prior to decryption</li>
 * </ul>
 */
public final class PasetoV3Local {

    public static final String HEADER = "v3.local.";
    public static final int KEY_LENGTH = 32;
    public static final int NONCE_LENGTH = 32;
    public static final int TAG_LENGTH = 48;

    private static final byte[] HEADER_BYTES = HEADER.getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENC_INFO_PREFIX = "paseto-encryption-key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AUTH_INFO_PREFIX = "paseto-auth-key-for-aead".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EMPTY = new byte[0];

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private PasetoV3Local() {
    }

    /**
     * Generates a cryptographically strong random 32-byte (256-bit) symmetric key.
     */
    public static byte[] generateKey() {
        byte[] key = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    /**
     * Encrypts {@code plaintext} into a {@code v3.local.} token with no footer or implicit assertion.
     */
    public static String encrypt(byte[] key, byte[] plaintext) {
        return encrypt(key, plaintext, EMPTY, EMPTY);
    }

    /**
     * Encrypts {@code plaintext} into a {@code v3.local.} token with optional {@code footer}
     * and {@code implicitAssertion}.
     */
    public static String encrypt(byte[] key, byte[] plaintext, byte[] footer, byte[] implicitAssertion) {
        byte[] nonce = new byte[NONCE_LENGTH];
        SECURE_RANDOM.nextBytes(nonce);
        return encryptWithNonce(key, plaintext, footer, implicitAssertion, nonce);
    }

    /**
     * Convenience method encrypting a UTF-8 plaintext string with no footer or implicit assertion.
     */
    public static String encryptString(byte[] key, String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        return encrypt(key, plaintext.getBytes(StandardCharsets.UTF_8), EMPTY, EMPTY);
    }

    /**
     * Convenience method encrypting UTF-8 plaintext, optional footer, and optional implicit assertion.
     */
    public static String encryptString(byte[] key, String plaintext, String footer, String implicitAssertion) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] f = footer == null ? EMPTY : footer.getBytes(StandardCharsets.UTF_8);
        byte[] i = implicitAssertion == null ? EMPTY : implicitAssertion.getBytes(StandardCharsets.UTF_8);
        return encrypt(key, plaintext.getBytes(StandardCharsets.UTF_8), f, i);
    }

    /**
     * Decrypts and authenticates a {@code v3.local.} token with empty implicit assertion.
     */
    public static PasetoV3Token decrypt(byte[] key, String token) {
        return decrypt(key, token, null, EMPTY);
    }

    /**
     * Decrypts and authenticates a {@code v3.local.} token with the specified implicit assertion.
     */
    public static PasetoV3Token decrypt(byte[] key, String token, byte[] implicitAssertion) {
        return decrypt(key, token, null, implicitAssertion);
    }

    /**
     * Decrypts and authenticates a {@code v3.local.} token, optionally verifying that the token's
     * footer equals {@code expectedFooter} (when non-null) and binding {@code implicitAssertion}.
     */
    public static PasetoV3Token decrypt(byte[] key, String token, byte[] expectedFooter, byte[] implicitAssertion) {
        validateKey(key);
        if (token == null || !token.startsWith(HEADER)) {
            throw new PasetoException("Invalid PASETO token: must start with '" + HEADER + "'");
        }

        String remainder = token.substring(HEADER.length());
        int dotIdx = remainder.indexOf('.');
        String payloadPart;
        byte[] footerBytes;

        if (dotIdx < 0) {
            payloadPart = remainder;
            footerBytes = EMPTY;
        } else {
            if (remainder.indexOf('.', dotIdx + 1) >= 0) {
                throw new PasetoException("Invalid PASETO token: unexpected extra '.' delimiter");
            }
            payloadPart = remainder.substring(0, dotIdx);
            String footerPart = remainder.substring(dotIdx + 1);
            if (footerPart.isEmpty()) {
                throw new PasetoException("Invalid PASETO token: empty footer segment after '.'");
            }
            footerBytes = decodeBase64UrlStrict(footerPart);
        }

        if (payloadPart.isEmpty()) {
            throw new PasetoException("Invalid PASETO token: empty payload segment");
        }
        if (expectedFooter != null && !MessageDigest.isEqual(footerBytes, expectedFooter)) {
            throw new PasetoException("PASETO footer mismatch");
        }

        byte[] decoded = decodeBase64UrlStrict(payloadPart);
        if (decoded.length < NONCE_LENGTH + TAG_LENGTH) {
            throw new PasetoException("Invalid PASETO token: decoded payload too short");
        }

        byte[] nonce = Arrays.copyOfRange(decoded, 0, NONCE_LENGTH);
        int cipherLen = decoded.length - NONCE_LENGTH - TAG_LENGTH;
        byte[] ciphertext = Arrays.copyOfRange(decoded, NONCE_LENGTH, NONCE_LENGTH + cipherLen);
        byte[] tag = Arrays.copyOfRange(decoded, NONCE_LENGTH + cipherLen, decoded.length);
        byte[] implicit = implicitAssertion == null ? EMPTY : implicitAssertion;

        byte[] encMaterial = null;
        byte[] ek = null;
        byte[] n2 = null;
        byte[] ak = null;
        try {
            encMaterial = HkdfSha384.derive(key, null, concat(ENC_INFO_PREFIX, nonce), 48);
            ek = Arrays.copyOfRange(encMaterial, 0, 32);
            n2 = Arrays.copyOfRange(encMaterial, 32, 48);
            ak = HkdfSha384.derive(key, null, concat(AUTH_INFO_PREFIX, nonce), 48);

            byte[] preAuth = PaeEncoder.encode(HEADER_BYTES, nonce, ciphertext, footerBytes, implicit);
            byte[] expectedTag = HkdfSha384.hmacSha384(ak, preAuth);

            if (!MessageDigest.isEqual(tag, expectedTag)) {
                throw new PasetoException("PASETO authentication tag verification failed");
            }

            byte[] plaintext = aes256Ctr(Cipher.DECRYPT_MODE, ek, n2, ciphertext);
            return new PasetoV3Token(plaintext, footerBytes);
        } finally {
            zeroize(encMaterial);
            zeroize(ek);
            zeroize(n2);
            zeroize(ak);
        }
    }

    /**
     * Extracts the raw footer bytes from a {@code v3.local.} token BEFORE authentication
     * (e.g. to look up a key identifier {@code kid} in a key ring).
     *
     * <p><strong>Warning:</strong> The returned footer is unverified until {@link #decrypt} succeeds.
     */
    public static byte[] extractUnverifiedFooter(String token) {
        if (token == null || !token.startsWith(HEADER)) {
            throw new PasetoException("Invalid PASETO token: must start with '" + HEADER + "'");
        }
        String remainder = token.substring(HEADER.length());
        int dotIdx = remainder.indexOf('.');
        if (dotIdx < 0) {
            return EMPTY;
        }
        if (remainder.indexOf('.', dotIdx + 1) >= 0) {
            throw new PasetoException("Invalid PASETO token: unexpected extra '.' delimiter");
        }
        String footerPart = remainder.substring(dotIdx + 1);
        if (footerPart.isEmpty()) {
            throw new PasetoException("Invalid PASETO token: empty footer segment after '.'");
        }
        return decodeBase64UrlStrict(footerPart);
    }

    // Package-private for deterministic test vector verification
    static String encryptWithNonce(byte[] key, byte[] plaintext, byte[] footer, byte[] implicitAssertion, byte[] nonce) {
        validateKey(key);
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("Nonce must be exactly " + NONCE_LENGTH + " bytes");
        }

        byte[] f = footer == null ? EMPTY : footer;
        byte[] i = implicitAssertion == null ? EMPTY : implicitAssertion;

        byte[] encMaterial = null;
        byte[] ek = null;
        byte[] n2 = null;
        byte[] ak = null;
        try {
            encMaterial = HkdfSha384.derive(key, null, concat(ENC_INFO_PREFIX, nonce), 48);
            ek = Arrays.copyOfRange(encMaterial, 0, 32);
            n2 = Arrays.copyOfRange(encMaterial, 32, 48);
            ak = HkdfSha384.derive(key, null, concat(AUTH_INFO_PREFIX, nonce), 48);

            byte[] ciphertext = aes256Ctr(Cipher.ENCRYPT_MODE, ek, n2, plaintext);
            byte[] preAuth = PaeEncoder.encode(HEADER_BYTES, nonce, ciphertext, f, i);
            byte[] tag = HkdfSha384.hmacSha384(ak, preAuth);

            byte[] body = new byte[nonce.length + ciphertext.length + tag.length];
            System.arraycopy(nonce, 0, body, 0, nonce.length);
            System.arraycopy(ciphertext, 0, body, nonce.length, ciphertext.length);
            System.arraycopy(tag, 0, body, nonce.length + ciphertext.length, tag.length);

            String encodedBody = B64_ENCODER.encodeToString(body);
            if (f.length == 0) {
                return HEADER + encodedBody;
            }
            return HEADER + encodedBody + "." + B64_ENCODER.encodeToString(f);
        } finally {
            zeroize(encMaterial);
            zeroize(ek);
            zeroize(n2);
            zeroize(ak);
        }
    }

    private static byte[] aes256Ctr(int mode, byte[] ek, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(mode, new SecretKeySpec(ek, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new PasetoException("AES-256-CTR cipher operation failed", e);
        }
    }

    private static byte[] decodeBase64UrlStrict(String input) {
        if (input.indexOf('=') >= 0) {
            throw new PasetoException("Invalid Base64url encoding: padding '=' is forbidden in PASETO");
        }
        try {
            byte[] decoded = B64_DECODER.decode(input);
            // Reject non-canonical trailing bits
            if (!B64_ENCODER.encodeToString(decoded).equals(input)) {
                throw new PasetoException("Non-canonical Base64url encoding in PASETO token");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new PasetoException("Invalid Base64url encoding in PASETO token", e);
        }
    }

    private static void validateKey(byte[] key) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("PASETO v3.local key must be exactly " + KEY_LENGTH + " bytes");
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void zeroize(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
