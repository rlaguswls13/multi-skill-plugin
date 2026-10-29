package com.msp.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Parser and validator for JSON Web Tokens (JWT).
 */
public final class JwtParser {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private JwtSigner signer;
    private String expectedIssuer;
    private String expectedAudience;
    private String expectedSubject;
    private Duration clockSkew = Duration.ZERO;
    private Clock clock = Clock.systemUTC();

    public JwtParser verifyWith(JwtSigner signer) {
        this.signer = Objects.requireNonNull(signer, "signer must not be null");
        return this;
    }

    public JwtParser requireIssuer(String issuer) {
        this.expectedIssuer = issuer;
        return this;
    }

    public JwtParser requireAudience(String audience) {
        this.expectedAudience = audience;
        return this;
    }

    public JwtParser requireSubject(String subject) {
        this.expectedSubject = subject;
        return this;
    }

    public JwtParser clockSkew(Duration skew) {
        this.clockSkew = Objects.requireNonNull(skew, "skew must not be null");
        return this;
    }

    public JwtParser clock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        return this;
    }

    /**
     * Parses and validates the JWT string against configured rules and signature.
     *
     * @param tokenString compact JWT string ("header.claims.signature")
     * @return validated {@link JwtToken}
     * @throws JwtExpiredException    if the token is expired
     * @throws JwtSignatureException  if signature verification fails
     * @throws JwtPrematureException  if the token is not yet valid (nbf)
     * @throws JwtValidationException if expected claims do not match
     */
    public JwtToken parse(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            throw new JwtException("JWT token string must not be null or blank");
        }

        String[] parts = tokenString.trim().split("\\.", -1);
        if (parts.length != 3) {
            throw new JwtException("Invalid JWT format: expected 3 dot-separated parts but found " + parts.length);
        }

        Map<String, Object> headerMap;
        Map<String, Object> claimsMap;
        byte[] signatureBytes;

        try {
            String jsonHeader = new String(URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            headerMap = SimpleJson.parseObject(jsonHeader);
        } catch (Exception e) {
            throw new JwtException("Malformed JWT header: failed to decode Base64Url JSON", e);
        }

        try {
            String jsonPayload = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            claimsMap = SimpleJson.parseObject(jsonPayload);
        } catch (Exception e) {
            throw new JwtException("Malformed JWT payload: failed to decode Base64Url JSON", e);
        }

        try {
            signatureBytes = URL_DECODER.decode(parts[2]);
        } catch (Exception e) {
            throw new JwtSignatureException("Malformed JWT signature: invalid Base64Url encoding", e);
        }

        // Signature verification
        if (signer != null) {
            Object alg = headerMap.get("alg");
            if (alg == null || !signer.algorithm().equalsIgnoreCase(alg.toString())) {
                throw new JwtValidationException(String.format(
                        "Token algorithm '%s' does not match verifier algorithm '%s'",
                        alg, signer.algorithm()));
            }

            byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
            if (!signer.verify(signingInput, signatureBytes)) {
                throw new JwtSignatureException("JWT signature verification failed: signature is invalid or tampered");
            }
        }

        JwtClaims claims = new JwtClaims(claimsMap);
        Instant now = clock.instant();

        // Check expiration (exp)
        Instant exp = claims.expiresAt();
        if (exp != null && now.isAfter(exp.plus(clockSkew))) {
            throw new JwtExpiredException(exp);
        }

        // Check not-before (nbf)
        Instant nbf = claims.notBefore();
        if (nbf != null && now.isBefore(nbf.minus(clockSkew))) {
            throw new JwtPrematureException(nbf);
        }

        // Check issuer
        if (expectedIssuer != null && !expectedIssuer.equals(claims.issuer())) {
            throw new JwtValidationException(String.format(
                    "JWT issuer mismatch: expected '%s' but found '%s'", expectedIssuer, claims.issuer()));
        }

        // Check audience
        if (expectedAudience != null) {
            boolean matches = expectedAudience.equals(claims.audience()) || claims.audienceList().contains(expectedAudience);
            if (!matches) {
                throw new JwtValidationException(String.format(
                        "JWT audience mismatch: expected '%s' but found '%s'", expectedAudience, claims.audienceList()));
            }
        }

        // Check subject
        if (expectedSubject != null && !expectedSubject.equals(claims.subject())) {
            throw new JwtValidationException(String.format(
                    "JWT subject mismatch: expected '%s' but found '%s'", expectedSubject, claims.subject()));
        }

        return new JwtToken(headerMap, claims, signatureBytes, tokenString);
    }
}
