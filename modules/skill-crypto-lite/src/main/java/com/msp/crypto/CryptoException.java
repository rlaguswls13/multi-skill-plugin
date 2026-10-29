package com.msp.crypto;

/**
 * Unchecked exception thrown when a cryptographic operation fails
 * (e.g., authentication tag mismatch, bad ciphertext, key derivation failure).
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
