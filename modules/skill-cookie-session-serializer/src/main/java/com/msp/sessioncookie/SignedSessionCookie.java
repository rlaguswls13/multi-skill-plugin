package com.msp.sessioncookie;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Serializes {@code Map<String, String>} session attributes into a tamper-evident,
 * expiring, URL-safe cookie string protected by HMAC-SHA256.
 * <p>
 * Format:
 * <pre>{@code
 * Base64Url(Payload).EpochSeconds.Base64Url(HmacSignature)
 * }</pre>
 * <p>
 * Features:
 * <ul>
 *     <li>Tamper-proofing: Any modification to payload or expiry timestamp invalidates signature</li>
 *     <li>Time-to-Live (TTL): Rejects expired cookies automatically</li>
 *     <li>Constant-time comparison: Protects against timing attacks via {@link MessageDigest#isEqual(byte[], byte[])}</li>
 *     <li>Zero external dependencies: 100% Java 21 standard crypto and codecs</li>
 * </ul>
 */
public final class SignedSessionCookie {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private SignedSessionCookie() {
        // utility class
    }

    /**
     * Serializes session attributes with a duration TTL.
     *
     * @param sessionData key-value attributes (null values converted to empty string)
     * @param secretKey   HMAC signing secret key
     * @param ttl         duration until expiration
     * @return signed, expiring cookie string
     */
    public static String serialize(Map<String, String> sessionData, String secretKey, Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        return serialize(sessionData, secretKey, Instant.now().plus(ttl));
    }

    /**
     * Serializes session attributes with an explicit expiration instant.
     *
     * @param sessionData key-value attributes
     * @param secretKey   HMAC signing secret key
     * @param expiresAt   instant when cookie expires
     * @return signed, expiring cookie string
     */
    public static String serialize(Map<String, String> sessionData, String secretKey, Instant expiresAt) {
        Objects.requireNonNull(secretKey, "secretKey must not be null");
        if (secretKey.isBlank()) {
            throw new IllegalArgumentException("secretKey must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        String payloadString = encodeMap(sessionData);
        String payloadBase64 = B64_ENCODER.encodeToString(payloadString.getBytes(StandardCharsets.UTF_8));
        long epochSeconds = expiresAt.getEpochSecond();
        String dataToSign = payloadBase64 + "." + epochSeconds;

        byte[] signatureBytes = computeHmac(dataToSign, secretKey);
        String signatureBase64 = B64_ENCODER.encodeToString(signatureBytes);

        return dataToSign + "." + signatureBase64;
    }

    /**
     * Deserializes and validates a signed session cookie against the system clock.
     *
     * @param cookieValue cookie string to validate and deserialize
     * @param secretKey   HMAC signing secret key
     * @return Optional containing deserialized map if valid and not expired, or empty Optional otherwise
     */
    public static Optional<Map<String, String>> deserialize(String cookieValue, String secretKey) {
        return deserialize(cookieValue, secretKey, Clock.systemUTC());
    }

    /**
     * Deserializes and validates a signed session cookie against a specific Clock.
     *
     * @param cookieValue cookie string to validate
     * @param secretKey   HMAC signing secret key
     * @param clock       clock used to evaluate expiration
     * @return Optional containing map if valid, or Optional.empty()
     */
    public static Optional<Map<String, String>> deserialize(String cookieValue, String secretKey, Clock clock) {
        if (cookieValue == null || cookieValue.isBlank() || secretKey == null || secretKey.isBlank()) {
            return Optional.empty();
        }
        Objects.requireNonNull(clock, "clock must not be null");

        String[] parts = cookieValue.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }

        String payloadB64 = parts[0];
        String epochSecStr = parts[1];
        String signatureB64 = parts[2];

        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(epochSecStr);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        // Expiration check
        long nowSeconds = clock.instant().getEpochSecond();
        if (nowSeconds > epochSeconds) {
            return Optional.empty();
        }

        // Signature check
        String dataToSign = payloadB64 + "." + epochSecStr;
        byte[] expectedHmac = computeHmac(dataToSign, secretKey);
        byte[] actualHmac = decodeBase64Safe(signatureB64);
        if (actualHmac == null) {
            return Optional.empty();
        }

        // Constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(expectedHmac, actualHmac)) {
            return Optional.empty();
        }

        // Decode payload
        byte[] payloadBytes = decodeBase64Safe(payloadB64);
        if (payloadBytes == null) {
            return Optional.empty();
        }

        String payloadString = new String(payloadBytes, StandardCharsets.UTF_8);
        Map<String, String> data = decodeMap(payloadString);
        return Optional.of(Collections.unmodifiableMap(data));
    }

    /**
     * Extracts the expiration Instant from cookie without verifying signature.
     */
    public static Optional<Instant> getExpiration(String cookieValue) {
        if (cookieValue == null) {
            return Optional.empty();
        }
        String[] parts = cookieValue.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(parts[1])));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static byte[] computeHmac(String data, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    private static byte[] decodeBase64Safe(String b64) {
        try {
            return B64_DECODER.decode(b64);
        } catch (IllegalArgumentException e) {
            // Fallback to standard decoder if padding or RFC standard alphabet was used
            try {
                return Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private static String encodeMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            first = false;
            String key = entry.getKey() != null ? entry.getKey() : "";
            String val = entry.getValue() != null ? entry.getValue() : "";
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(val, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static Map<String, String> decodeMap(String str) {
        if (str == null || str.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        String[] pairs = str.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq != -1) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                result.put(key, val);
            } else {
                String key = URLDecoder.decode(pair, StandardCharsets.UTF_8);
                result.put(key, "");
            }
        }
        return result;
    }
}
