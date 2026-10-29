package com.msp.jwt;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Unified facade for creating, parsing, and signing JSON Web Tokens (JWT).
 */
public final class JwtLite {

    private JwtLite() {
        // Utility class
    }

    /**
     * Creates a new fluent {@link JwtBuilder}.
     */
    public static JwtBuilder builder() {
        return new JwtBuilder();
    }

    /**
     * Creates a new configurable {@link JwtParser}.
     */
    public static JwtParser parser() {
        return new JwtParser();
    }

    /**
     * Creates an HS256 (HMAC-SHA256) signer using raw secret key bytes.
     */
    public static Hs256Signer hs256(byte[] secretKey) {
        return new Hs256Signer(secretKey);
    }

    /**
     * Creates an HS256 (HMAC-SHA256) signer using a UTF-8 string secret.
     */
    public static Hs256Signer hs256(String secretKey) {
        return new Hs256Signer(secretKey);
    }

    /**
     * Creates an RS256 (RSA with SHA-256) signer and verifier.
     */
    public static Rs256Signer rs256(PrivateKey privateKey, PublicKey publicKey) {
        return new Rs256Signer(privateKey, publicKey);
    }

    /**
     * Creates an RS256 verifier with a public key.
     */
    public static Rs256Signer rs256(PublicKey publicKey) {
        return new Rs256Signer(publicKey);
    }

    /**
     * Creates an RS256 signer with a private key.
     */
    public static Rs256Signer rs256(PrivateKey privateKey) {
        return new Rs256Signer(privateKey);
    }
}
