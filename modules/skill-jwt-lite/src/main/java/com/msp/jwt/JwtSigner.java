package com.msp.jwt;

/**
 * Strategy interface for cryptographic signing and verification of JWTs.
 */
public interface JwtSigner {

    /**
     * Standard JWA algorithm name (e.g., "HS256", "RS256").
     */
    String algorithm();

    /**
     * Digitally signs the given data.
     *
     * @param data byte array to sign
     * @return raw signature bytes
     */
    byte[] sign(byte[] data);

    /**
     * Verifies the signature against the given data.
     *
     * @param data      byte array to verify
     * @param signature signature bytes to check
     * @return true if valid, false otherwise
     */
    boolean verify(byte[] data, byte[] signature);
}
