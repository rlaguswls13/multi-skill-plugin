package com.msp.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Generates and verifies HMAC-SHA256 signatures for webhook payloads to guarantee authenticity and integrity.
 */
public class WebhookSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final byte[] secretBytes;

    public WebhookSigner(byte[] secretBytes) {
        Objects.requireNonNull(secretBytes, "secretBytes cannot be null");
        if (secretBytes.length == 0) {
            throw new IllegalArgumentException("Secret key cannot be empty");
        }
        this.secretBytes = secretBytes.clone();
    }

    public WebhookSigner(String secret) {
        this(Objects.requireNonNull(secret, "secret cannot be null").getBytes(StandardCharsets.UTF_8));
    }

    public static WebhookSigner of(String secret) {
        return new WebhookSigner(secret);
    }

    public static WebhookSigner of(byte[] secretBytes) {
        return new WebhookSigner(secretBytes);
    }

    /**
     * Computes the HMAC-SHA256 signature bytes for the given payload.
     */
    public byte[] signBytes(byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretBytes, HMAC_SHA256));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    /**
     * Returns the hex-encoded HMAC-SHA256 signature for the given payload string.
     */
    public String sign(String payload) {
        byte[] data = Objects.requireNonNull(payload, "payload cannot be null").getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(signBytes(data));
    }

    /**
     * Formats the signature in GitHub-style format: "sha256=" + hexSignature.
     */
    public String signHub(String payload) {
        return "sha256=" + sign(payload);
    }

    /**
     * Verifies that the signature matches the calculated HMAC-SHA256 using constant-time comparison.
     */
    public boolean verify(String payload, String signature) {
        if (payload == null || signature == null) {
            return false;
        }

        String cleanedSignature = signature.trim();
        if (cleanedSignature.startsWith("sha256=")) {
            cleanedSignature = cleanedSignature.substring(7);
        }

        String expectedSignature = sign(payload);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = cleanedSignature.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
