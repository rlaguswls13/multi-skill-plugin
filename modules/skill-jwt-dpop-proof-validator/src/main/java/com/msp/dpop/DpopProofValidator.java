package com.msp.dpop;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Validates RFC 9449 OAuth 2.0 DPoP (Demonstrating Proof-of-Possession) Proof JWTs.
 */
public final class DpopProofValidator {

    public static final Duration DEFAULT_CLOCK_SKEW = Duration.ofMinutes(5);
    public static final Set<String> DEFAULT_SUPPORTED_ALGORITHMS = Set.of("RS256", "ES256");

    private final Duration clockSkewTolerance;
    private final Set<String> supportedAlgorithms;
    private final ReplayCache replayCache;

    public DpopProofValidator() {
        this(DEFAULT_CLOCK_SKEW, DEFAULT_SUPPORTED_ALGORITHMS, new ReplayCache());
    }

    public DpopProofValidator(Duration clockSkewTolerance, Set<String> supportedAlgorithms, ReplayCache replayCache) {
        this.clockSkewTolerance = Objects.requireNonNull(clockSkewTolerance, "clockSkewTolerance cannot be null");
        this.supportedAlgorithms = Collections.unmodifiableSet(new HashSet<>(supportedAlgorithms));
        this.replayCache = Objects.requireNonNull(replayCache, "replayCache cannot be null");
    }

    /**
     * Validates a DPoP proof JWT against expected HTTP method and URI.
     */
    public DpopValidationResult validate(String dpopProofJwt, String expectedMethod, String expectedUri) {
        return validate(dpopProofJwt, expectedMethod, expectedUri, null);
    }

    /**
     * Validates a DPoP proof JWT against expected HTTP method, URI, and bound access token.
     */
    @SuppressWarnings("unchecked")
    public DpopValidationResult validate(
            String dpopProofJwt,
            String expectedMethod,
            String expectedUri,
            String expectedAccessToken
    ) {
        if (dpopProofJwt == null || dpopProofJwt.isBlank()) {
            return DpopValidationResult.failure("DPoP proof JWT is null or empty");
        }
        if (expectedMethod == null || expectedMethod.isBlank()) {
            return DpopValidationResult.failure("Expected HTTP method is missing");
        }
        if (expectedUri == null || expectedUri.isBlank()) {
            return DpopValidationResult.failure("Expected HTTP URI is missing");
        }

        String[] parts = dpopProofJwt.trim().split("\\.");
        if (parts.length != 3) {
            return DpopValidationResult.failure("Invalid JWT format: expected 3 dot-separated parts");
        }

        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String signatureB64 = parts[2];

        Map<String, Object> header;
        Map<String, Object> payload;
        byte[] signatureBytes;

        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(headerB64), StandardCharsets.UTF_8);
            header = JsonLite.parseObject(headerJson);
            String payloadJson = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            payload = JsonLite.parseObject(payloadJson);
            signatureBytes = Base64.getUrlDecoder().decode(signatureB64);
        } catch (Exception e) {
            return DpopValidationResult.failure("Malformed Base64URL or JSON in DPoP JWT: " + e.getMessage());
        }

        // 1. Header validations
        // 'typ' MUST be 'dpop+jwt' (RFC 9449 §4.2)
        Object typ = header.get("typ");
        if (!"dpop+jwt".equals(typ)) {
            return DpopValidationResult.failure("Invalid header 'typ': must be 'dpop+jwt', got: " + typ);
        }

        // 'alg' must be supported asymmetric algorithm
        Object algObj = header.get("alg");
        if (!(algObj instanceof String alg) || !supportedAlgorithms.contains(alg.toUpperCase(Locale.ROOT))) {
            return DpopValidationResult.failure("Unsupported or missing 'alg': " + algObj);
        }

        // 'jwk' MUST be present and valid
        Object jwkObj = header.get("jwk");
        if (!(jwkObj instanceof Map<?, ?> jwkRaw)) {
            return DpopValidationResult.failure("Missing or invalid 'jwk' header parameter");
        }
        Map<String, Object> jwk = (Map<String, Object>) jwkRaw;

        PublicKey publicKey;
        String thumbprint;
        try {
            publicKey = DpopKeyUtil.parseJwk(jwk);
            thumbprint = DpopKeyUtil.computeThumbprint(jwk);
        } catch (Exception e) {
            return DpopValidationResult.failure("Invalid public JWK in header: " + e.getMessage());
        }

        // 2. Payload validations
        // 'jti' unique identifier
        Object jtiObj = payload.get("jti");
        if (!(jtiObj instanceof String jti) || jti.isBlank()) {
            return DpopValidationResult.failure("Missing or invalid 'jti' claim");
        }

        // 'htm' HTTP method
        Object htmObj = payload.get("htm");
        if (!(htmObj instanceof String htm) || !htm.equalsIgnoreCase(expectedMethod.trim())) {
            return DpopValidationResult.failure("HTTP method mismatch: expected '" + expectedMethod + "', claim 'htm' was '" + htmObj + "'");
        }

        // 'htu' HTTP URI (normalized comparison: without query/fragment)
        Object htuObj = payload.get("htu");
        if (!(htuObj instanceof String htu) || !normalizeUri(htu).equals(normalizeUri(expectedUri))) {
            return DpopValidationResult.failure("HTTP URI mismatch: expected '" + normalizeUri(expectedUri) + "', claim 'htu' was '" + (htuObj != null ? normalizeUri((String) htuObj) : null) + "'");
        }

        // 'iat' timestamp and clock skew tolerance
        Object iatObj = payload.get("iat");
        if (!(iatObj instanceof Number iatNum)) {
            return DpopValidationResult.failure("Missing or invalid 'iat' claim");
        }
        long iatSeconds = iatNum.longValue();
        Instant iatInstant = Instant.ofEpochSecond(iatSeconds);
        Instant now = Instant.now();

        if (iatInstant.isBefore(now.minus(clockSkewTolerance))) {
            return DpopValidationResult.failure("DPoP proof expired: 'iat' " + iatInstant + " is older than skew tolerance");
        }
        if (iatInstant.isAfter(now.plus(clockSkewTolerance))) {
            return DpopValidationResult.failure("DPoP proof issued in future: 'iat' " + iatInstant + " is ahead of current time");
        }

        // Check for replay attack
        Instant expiresAt = iatInstant.plus(clockSkewTolerance.multipliedBy(2));
        if (!replayCache.testAndSet(jti, expiresAt)) {
            return DpopValidationResult.failure("DPoP proof replay detected for JTI: " + jti);
        }

        // 'ath' access token hash validation if required
        Optional<String> athOptional = Optional.ofNullable((String) payload.get("ath"));
        if (expectedAccessToken != null) {
            if (athOptional.isEmpty()) {
                return DpopValidationResult.failure("Missing required 'ath' claim for bound access token");
            }
            String expectedAth = DpopKeyUtil.computeAccessTokenHash(expectedAccessToken);
            if (!expectedAth.equals(athOptional.get())) {
                return DpopValidationResult.failure("Access token hash mismatch ('ath' does not match SHA-256 of token)");
            }
        }

        // 3. Cryptographic Signature Verification
        byte[] signingInput = (headerB64 + "." + payloadB64).getBytes(StandardCharsets.US_ASCII);
        try {
            boolean verified = DpopKeyUtil.verifySignature(alg, publicKey, signingInput, signatureBytes);
            if (!verified) {
                return DpopValidationResult.failure("Cryptographic signature verification failed");
            }
        } catch (Exception e) {
            return DpopValidationResult.failure("Signature verification exception: " + e.getMessage());
        }

        DpopClaims claims = new DpopClaims(jti, (String) htmObj, (String) htuObj, iatSeconds, athOptional, payload);
        return DpopValidationResult.success(thumbprint, claims);
    }

    private static String normalizeUri(String uriString) {
        try {
            URI uri = URI.create(uriString.trim());
            // Strip query and fragment
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            int port = uri.getPort();
            String path = uri.getPath() != null ? uri.getPath() : "";
            if (path.isEmpty()) path = "/";

            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port != -1 && !((port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme)))) {
                sb.append(":").append(port);
            }
            sb.append(path);
            return sb.toString();
        } catch (Exception e) {
            // Fallback: simple string manipulation
            int qIdx = uriString.indexOf('?');
            String clean = qIdx != -1 ? uriString.substring(0, qIdx) : uriString;
            int fIdx = clean.indexOf('#');
            return fIdx != -1 ? clean.substring(0, fIdx) : clean;
        }
    }
}
