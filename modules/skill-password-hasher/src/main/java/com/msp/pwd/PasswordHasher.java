package com.msp.pwd;

/**
 * Interface for cryptographic one-way password hashing and verification.
 */
public interface PasswordHasher {

    /**
     * Hashes a raw password using a salt and cryptographic work factor.
     *
     * @param rawPassword raw character sequence
     * @return formatted hash string containing algorithm metadata, salt, and digest
     */
    String hash(CharSequence rawPassword);

    /**
     * Verifies whether a raw password matches an encoded hash.
     *
     * @param rawPassword raw character sequence
     * @param encodedHash previously generated hash string
     * @return true if password matches, false otherwise
     */
    boolean verify(CharSequence rawPassword, String encodedHash);
}
