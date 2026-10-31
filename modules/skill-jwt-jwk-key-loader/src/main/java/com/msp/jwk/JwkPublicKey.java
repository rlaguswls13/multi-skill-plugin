package com.msp.jwk;

import java.security.PublicKey;
import java.util.Objects;

/**
 * Represents a parsed and reconstructed JSON Web Key (JWK) public key.
 *
 * @param kid       Key ID header parameter (optional, may be null or empty)
 * @param kty       Key type (e.g. "RSA", "EC")
 * @param use       Public key use (e.g. "sig", "enc")
 * @param alg       Algorithm intended for use with the key (e.g. "RS256")
 * @param publicKey The reconstructed Java Cryptography Architecture (JCA) {@link PublicKey}
 */
public record JwkPublicKey(
        String kid,
        String kty,
        String use,
        String alg,
        PublicKey publicKey
) {
    public JwkPublicKey {
        Objects.requireNonNull(kty, "kty (key type) cannot be null");
        Objects.requireNonNull(publicKey, "publicKey cannot be null");
    }
}
