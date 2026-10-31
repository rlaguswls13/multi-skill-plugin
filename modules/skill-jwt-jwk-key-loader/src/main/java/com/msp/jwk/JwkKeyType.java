package com.msp.jwk;

/**
 * Standard RFC 7517 Key Types (kty).
 */
public enum JwkKeyType {
    /** RSA public key */
    RSA,
    /** Elliptic Curve public key */
    EC,
    /** Octet sequence (symmetric key) */
    OCT;

    /**
     * Case-insensitive matching for kty strings.
     */
    public static JwkKeyType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Key type cannot be null");
        }
        return switch (value.trim().toUpperCase()) {
            case "RSA" -> RSA;
            case "EC" -> EC;
            case "OCT" -> OCT;
            default -> throw new IllegalArgumentException("Unsupported or unknown JWK key type: " + value);
        };
    }
}
