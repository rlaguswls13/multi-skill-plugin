package com.msp.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for creating and signing RFC 7519 JSON Web Tokens (JWT).
 */
public final class JwtBuilder {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Map<String, Object> header = new LinkedHashMap<>();
    private final Map<String, Object> claims = new LinkedHashMap<>();
    private JwtSigner signer;

    public JwtBuilder() {
        header.put("typ", "JWT");
    }

    public JwtBuilder header(String name, Object value) {
        Objects.requireNonNull(name, "header name must not be null");
        header.put(name, value);
        return this;
    }

    public JwtBuilder subject(String subject) {
        claims.put(JwtClaims.CLAIM_SUBJECT, subject);
        return this;
    }

    public JwtBuilder issuer(String issuer) {
        claims.put(JwtClaims.CLAIM_ISSUER, issuer);
        return this;
    }

    public JwtBuilder audience(String audience) {
        claims.put(JwtClaims.CLAIM_AUDIENCE, audience);
        return this;
    }

    public JwtBuilder issuedAt(Instant issuedAt) {
        if (issuedAt != null) {
            claims.put(JwtClaims.CLAIM_ISSUED_AT, issuedAt.getEpochSecond());
        }
        return this;
    }

    public JwtBuilder expiresAt(Instant expiresAt) {
        if (expiresAt != null) {
            claims.put(JwtClaims.CLAIM_EXPIRATION, expiresAt.getEpochSecond());
        }
        return this;
    }

    public JwtBuilder notBefore(Instant notBefore) {
        if (notBefore != null) {
            claims.put(JwtClaims.CLAIM_NOT_BEFORE, notBefore.getEpochSecond());
        }
        return this;
    }

    public JwtBuilder id(String id) {
        claims.put(JwtClaims.CLAIM_JWT_ID, id);
        return this;
    }

    public JwtBuilder claim(String name, Object value) {
        Objects.requireNonNull(name, "claim name must not be null");
        claims.put(name, value);
        return this;
    }

    public JwtBuilder claims(Map<String, Object> additionalClaims) {
        if (additionalClaims != null) {
            claims.putAll(additionalClaims);
        }
        return this;
    }

    public JwtBuilder signWith(JwtSigner signer) {
        this.signer = Objects.requireNonNull(signer, "signer must not be null");
        return this;
    }

    /**
     * Serializes and digitally signs the JWT, returning the compact representation.
     *
     * @return 3-part Base64Url string ("header.claims.signature")
     */
    public String compact() {
        if (signer == null) {
            throw new JwtException("Cannot compact JWT: signer has not been specified");
        }

        header.put("alg", signer.algorithm());

        String jsonHeader = SimpleJson.toJson(header);
        String jsonPayload = SimpleJson.toJson(claims);

        String encodedHeader = URL_ENCODER.encodeToString(jsonHeader.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = URL_ENCODER.encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));

        String signingInput = encodedHeader + "." + encodedPayload;
        byte[] signature = signer.sign(signingInput.getBytes(StandardCharsets.US_ASCII));
        String encodedSignature = URL_ENCODER.encodeToString(signature);

        return signingInput + "." + encodedSignature;
    }
}
