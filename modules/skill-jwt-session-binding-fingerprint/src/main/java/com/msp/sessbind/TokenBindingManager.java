package com.msp.sessbind;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Service for computing and cryptographically verifying client session binding claims in JWTs.
 */
public class TokenBindingManager {

    public static final String CLAIM_BINDING_HASH = "bnd";
    public static final String CLAIM_BINDING_IP = "bnd_ip";
    public static final String CLAIM_BINDING_MODE = "bnd_mode";
    public static final String CLAIM_BINDING_TLS = "bnd_tls";
    public static final String CLAIM_BINDING_DEV = "bnd_dev";

    /**
     * Normalizes a raw User-Agent header by trimming, lowercasing, and collapsing whitespace.
     */
    public static String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        return userAgent.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Resolves the canonical IP representation according to the specified binding mode.
     */
    public static String resolveIpBinding(String ipAddress, IpBindingMode mode) {
        if (mode == null || mode == IpBindingMode.DISABLED || ipAddress == null || ipAddress.isBlank()) {
            return "none";
        }
        if (mode == IpBindingMode.EXACT) {
            return ipAddress.trim();
        }
        return IpSubnetHelper.getSubnet(ipAddress);
    }

    /**
     * Builds the deterministic canonical payload string for a client fingerprint context.
     */
    public static String buildCanonicalString(ClientFingerprintContext context) {
        Objects.requireNonNull(context, "context must not be null");

        StringBuilder sb = new StringBuilder();
        sb.append("ip=").append(resolveIpBinding(context.ipAddress(), context.ipBindingMode())).append(";");
        sb.append("ua=").append(normalizeUserAgent(context.userAgent())).append(";");

        if (context.tlsCertThumbprint() != null && !context.tlsCertThumbprint().isBlank()) {
            sb.append("tls=").append(context.tlsCertThumbprint().trim().toLowerCase(Locale.ROOT)).append(";");
        }
        if (context.tlsChannelBinding() != null && !context.tlsChannelBinding().isBlank()) {
            sb.append("cb=").append(context.tlsChannelBinding().trim()).append(";");
        }
        if (context.deviceKeyId() != null && !context.deviceKeyId().isBlank()) {
            sb.append("dev=").append(context.deviceKeyId().trim()).append(";");
        }

        if (context.extraAttributes() != null && !context.extraAttributes().isEmpty()) {
            List<String> sortedKeys = new ArrayList<>(context.extraAttributes().keySet());
            Collections.sort(sortedKeys);
            for (String key : sortedKeys) {
                sb.append("attr.").append(key).append("=").append(context.extraAttributes().get(key)).append(";");
            }
        }

        return sb.toString();
    }

    /**
     * Computes the cryptographic binding hash (HMAC-SHA256 if secret/salt provided, else SHA-256).
     *
     * @param context      Client fingerprint context
     * @param secretOrSalt Optional HMAC secret key or salt bytes (can be null for plain SHA-256)
     * @return Hex-encoded cryptographic binding hash
     */
    public static String computeBindingHash(ClientFingerprintContext context, byte[] secretOrSalt) {
        String canonical = buildCanonicalString(context);
        byte[] payload = canonical.getBytes(StandardCharsets.UTF_8);

        try {
            byte[] digestBytes;
            if (secretOrSalt != null && secretOrSalt.length > 0) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secretOrSalt, "HmacSHA256"));
                digestBytes = mac.doFinal(payload);
            } else {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                digestBytes = md.digest(payload);
            }
            return HexFormat.of().formatHex(digestBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate cryptographic hash", e);
        }
    }

    /**
     * Creates JWT claims map for binding token to the client context.
     *
     * @param context      Client fingerprint context
     * @param secretOrSalt Secret key or salt bytes
     * @return Map of JWT claims to inject into token payload
     */
    public static Map<String, Object> createBindingClaims(ClientFingerprintContext context, byte[] secretOrSalt) {
        Map<String, Object> claims = new HashMap<>();
        String hash = computeBindingHash(context, secretOrSalt);
        claims.put(CLAIM_BINDING_HASH, hash);
        claims.put(CLAIM_BINDING_MODE, context.ipBindingMode().name());

        if (context.ipBindingMode() != IpBindingMode.DISABLED && context.ipAddress() != null) {
            claims.put(CLAIM_BINDING_IP, resolveIpBinding(context.ipAddress(), context.ipBindingMode()));
        }
        if (context.tlsCertThumbprint() != null) {
            claims.put(CLAIM_BINDING_TLS, context.tlsCertThumbprint().trim().toLowerCase(Locale.ROOT));
        }
        if (context.deviceKeyId() != null) {
            claims.put(CLAIM_BINDING_DEV, context.deviceKeyId().trim());
        }

        return Collections.unmodifiableMap(claims);
    }

    /**
     * Validates client connection context against the claims embedded in the JWT.
     *
     * @param currentContext Current client request context
     * @param tokenClaims    Claims extracted from JWT
     * @param secretOrSalt   HMAC secret key or salt bytes
     * @return Detailed validation outcome
     */
    public static TokenBindingValidationResult validate(
            ClientFingerprintContext currentContext,
            Map<String, Object> tokenClaims,
            byte[] secretOrSalt
    ) {
        if (tokenClaims == null || !tokenClaims.containsKey(CLAIM_BINDING_HASH)) {
            return TokenBindingValidationResult.failure(
                    MismatchType.MISSING_BINDING_CLAIM,
                    MismatchSeverity.HIGH,
                    null,
                    null,
                    "JWT does not contain required '" + CLAIM_BINDING_HASH + "' claim"
            );
        }

        String expectedHash = String.valueOf(tokenClaims.get(CLAIM_BINDING_HASH));

        // 1. Validate TLS Channel Binding if present in claims
        if (tokenClaims.containsKey(CLAIM_BINDING_TLS)) {
            String tokenTls = String.valueOf(tokenClaims.get(CLAIM_BINDING_TLS));
            String currentTls = currentContext.tlsCertThumbprint() != null
                    ? currentContext.tlsCertThumbprint().trim().toLowerCase(Locale.ROOT)
                    : null;
            if (!Objects.equals(tokenTls, currentTls)) {
                return TokenBindingValidationResult.failure(
                        MismatchType.TLS_CHANNEL_MISMATCH,
                        MismatchSeverity.CRITICAL,
                        tokenTls,
                        currentTls,
                        "TLS certificate thumbprint mismatch (expected: " + tokenTls + ", actual: " + currentTls + ")"
                );
            }
        }

        // 2. Validate Device Key ID if present in claims
        if (tokenClaims.containsKey(CLAIM_BINDING_DEV)) {
            String tokenDev = String.valueOf(tokenClaims.get(CLAIM_BINDING_DEV));
            String currentDev = currentContext.deviceKeyId();
            if (!Objects.equals(tokenDev, currentDev)) {
                return TokenBindingValidationResult.failure(
                        MismatchType.DEVICE_KEY_MISMATCH,
                        MismatchSeverity.CRITICAL,
                        tokenDev,
                        currentDev,
                        "Client device key mismatch (expected: " + tokenDev + ", actual: " + currentDev + ")"
                );
            }
        }

        // 3. Check IP subnet if recorded
        if (tokenClaims.containsKey(CLAIM_BINDING_IP)) {
            String tokenIpSubnet = String.valueOf(tokenClaims.get(CLAIM_BINDING_IP));
            String currentIpSubnet = resolveIpBinding(currentContext.ipAddress(), currentContext.ipBindingMode());
            if (!Objects.equals(tokenIpSubnet, currentIpSubnet)) {
                return TokenBindingValidationResult.failure(
                        MismatchType.IP_SUBNET_MISMATCH,
                        MismatchSeverity.MEDIUM,
                        tokenIpSubnet,
                        currentIpSubnet,
                        "Client IP subnet changed (expected: " + tokenIpSubnet + ", actual: " + currentIpSubnet + ")"
                );
            }
        }

        // 4. Verify overall cryptographic hash
        String actualHash = computeBindingHash(currentContext, secretOrSalt);
        if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8), actualHash.getBytes(StandardCharsets.UTF_8))) {
            // Determine if User Agent was the culprit
            return TokenBindingValidationResult.failure(
                    MismatchType.USER_AGENT_MISMATCH,
                    MismatchSeverity.HIGH,
                    expectedHash,
                    actualHash,
                    "Cryptographic fingerprint mismatch (likely User-Agent or connection profile difference)"
            );
        }

        return TokenBindingValidationResult.success(actualHash);
    }
}
