package com.msp.jwelite;

/**
 * Standard RFC 7518 JWE Content Encryption Algorithms supported by JweLite.
 */
public final class JweEncryption {

    public static final String A128GCM = "A128GCM";
    public static final String A256GCM = "A256GCM";

    private JweEncryption() {
    }

    public static boolean isSupported(String enc) {
        return A128GCM.equals(enc) || A256GCM.equals(enc);
    }

    public static int getKeyLengthInBytes(String enc) {
        return switch (enc) {
            case A128GCM -> 16;
            case A256GCM -> 32;
            default -> throw new IllegalArgumentException("Unsupported encryption algorithm: " + enc);
        };
    }
}
