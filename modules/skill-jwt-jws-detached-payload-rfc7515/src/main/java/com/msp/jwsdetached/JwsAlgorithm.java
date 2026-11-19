package com.msp.jwsdetached;

/**
 * Supported JWS cryptographic signature algorithms for detached content signing and verification.
 */
public enum JwsAlgorithm {
    HS256("HS256", "HmacSHA256", true, 0),
    HS384("HS384", "HmacSHA384", true, 0),
    HS512("HS512", "HmacSHA512", true, 0),
    RS256("RS256", "SHA256withRSA", false, 0),
    ES256("ES256", "SHA256withECDSAinP1363Format", false, 64);

    private final String joseName;
    private final String jcaName;
    private final boolean hmac;
    private final int ecdsaRawSignatureLength;

    JwsAlgorithm(String joseName, String jcaName, boolean hmac, int ecdsaRawSignatureLength) {
        this.joseName = joseName;
        this.jcaName = jcaName;
        this.hmac = hmac;
        this.ecdsaRawSignatureLength = ecdsaRawSignatureLength;
    }

    public String joseName() {
        return joseName;
    }

    public String jcaName() {
        return jcaName;
    }

    public boolean isHmac() {
        return hmac;
    }

    public int ecdsaRawSignatureLength() {
        return ecdsaRawSignatureLength;
    }

    public static JwsAlgorithm fromJoseName(String name) {
        for (JwsAlgorithm alg : values()) {
            if (alg.joseName.equals(name)) {
                return alg;
            }
        }
        throw new JwsDetachedException("Unsupported JWS algorithm: " + name);
    }
}
