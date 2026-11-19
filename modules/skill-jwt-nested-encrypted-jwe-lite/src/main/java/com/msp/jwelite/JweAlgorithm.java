package com.msp.jwelite;

/**
 * Standard RFC 7518 JWE Key Management Algorithms supported by JweLite.
 */
public final class JweAlgorithm {

    public static final String RSA_OAEP = "RSA-OAEP";
    public static final String RSA_OAEP_256 = "RSA-OAEP-256";
    public static final String DIR = "dir";

    private JweAlgorithm() {
    }

    public static boolean isSupported(String alg) {
        return RSA_OAEP.equals(alg) || RSA_OAEP_256.equals(alg) || DIR.equals(alg);
    }
}
