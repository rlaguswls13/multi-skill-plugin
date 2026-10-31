package com.msp.jwk;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;

/**
 * Represents a single JSON Web Key (JWK) according to RFC 7517 / RFC 7518.
 * Supports RSA and EC public keys with pure Java decoding.
 */
public final class JwkKey {

    private final String kid;
    private final String kty;
    private final String use;
    private final String alg;
    private final Map<String, String> params;

    private volatile PublicKey cachedPublicKey;

    public JwkKey(String kid, String kty, String use, String alg, Map<String, String> params) {
        this.kid = kid;
        this.kty = Objects.requireNonNull(kty, "kty must not be null");
        this.use = use;
        this.alg = alg;
        this.params = params != null ? Map.copyOf(params) : Map.of();
    }

    public static JwkKey fromMap(Map<String, Object> map) {
        String kid = (String) map.get("kid");
        String kty = (String) map.get("kty");
        String use = (String) map.get("use");
        String alg = (String) map.get("alg");

        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("kid") && !key.equals("kty") && !key.equals("use") && !key.equals("alg")) {
                if (entry.getValue() instanceof String strVal) {
                    params.put(key, strVal);
                } else if (entry.getValue() != null) {
                    params.put(key, String.valueOf(entry.getValue()));
                }
            }
        }
        return new JwkKey(kid, kty, use, alg, params);
    }

    public static JwkKey fromPublicKey(String kid, PublicKey publicKey, String use, String alg) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Map<String, String> params = new LinkedHashMap<>();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();

        if (publicKey instanceof RSAPublicKey rsa) {
            byte[] modBytes = stripLeadingZeros(rsa.getModulus().toByteArray());
            byte[] expBytes = stripLeadingZeros(rsa.getPublicExponent().toByteArray());
            params.put("n", b64.encodeToString(modBytes));
            params.put("e", b64.encodeToString(expBytes));
            return new JwkKey(kid, "RSA", use, alg != null ? alg : "RS256", params);
        } else if (publicKey instanceof ECPublicKey ec) {
            ECPoint w = ec.getW();
            byte[] xBytes = stripLeadingZeros(w.getAffineX().toByteArray());
            byte[] yBytes = stripLeadingZeros(w.getAffineY().toByteArray());
            params.put("crv", "P-256");
            params.put("x", b64.encodeToString(xBytes));
            params.put("y", b64.encodeToString(yBytes));
            return new JwkKey(kid, "EC", use, alg != null ? alg : "ES256", params);
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + publicKey.getClass().getName());
        }
    }

    public String kid() {
        return kid;
    }

    public String getKid() {
        return kid;
    }

    public String kty() {
        return kty;
    }

    public String getKty() {
        return kty;
    }

    public String use() {
        return use;
    }

    public String getUse() {
        return use;
    }

    public String alg() {
        return alg;
    }

    public String getAlg() {
        return alg;
    }

    public String param(String name) {
        return params.get(name);
    }

    public Map<String, String> getParams() {
        return params;
    }

    /**
     * Converts this JWK into a standard Java {@link PublicKey}.
     * Supports RSA (via n and e) and EC (via crv, x, y).
     */
    public PublicKey toPublicKey() {
        if (cachedPublicKey != null) {
            return cachedPublicKey;
        }

        try {
            if ("RSA".equalsIgnoreCase(kty)) {
                cachedPublicKey = decodeRsaPublicKey();
            } else if ("EC".equalsIgnoreCase(kty)) {
                cachedPublicKey = decodeEcPublicKey();
            } else {
                throw new UnsupportedOperationException("Unsupported JWK key type: " + kty);
            }
            return cachedPublicKey;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JWK to PublicKey for kid=" + kid + ": " + e.getMessage(), e);
        }
    }

    private PublicKey decodeRsaPublicKey() throws Exception {
        String n = params.get("n");
        String e = params.get("e");
        if (n == null || e == null) {
            throw new IllegalArgumentException("RSA JWK must contain 'n' and 'e' parameters");
        }

        byte[] modulusBytes = Base64.getUrlDecoder().decode(n);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(e);

        BigInteger modulus = new BigInteger(1, modulusBytes);
        BigInteger exponent = new BigInteger(1, exponentBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private PublicKey decodeEcPublicKey() throws Exception {
        String crv = params.getOrDefault("crv", "P-256");
        String x = params.get("x");
        String y = params.get("y");
        if (x == null || y == null) {
            throw new IllegalArgumentException("EC JWK must contain 'x' and 'y' parameters");
        }

        byte[] xBytes = Base64.getUrlDecoder().decode(x);
        byte[] yBytes = Base64.getUrlDecoder().decode(y);

        BigInteger xCoord = new BigInteger(1, xBytes);
        BigInteger yCoord = new BigInteger(1, yBytes);
        ECPoint point = new ECPoint(xCoord, yCoord);

        String curveName = switch (crv) {
            case "P-256", "secp256r1" -> "secp256r1";
            case "P-384", "secp384r1" -> "secp384r1";
            case "P-521", "secp521r1" -> "secp521r1";
            default -> crv;
        };

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curveName));
        ECParameterSpec ecSpec = parameters.getParameterSpec(ECParameterSpec.class);

        ECPublicKeySpec spec = new ECPublicKeySpec(point, ecSpec);
        return KeyFactory.getInstance("EC").generatePublic(spec);
    }

    private static byte[] stripLeadingZeros(byte[] bytes) {
        int i = 0;
        while (i < bytes.length - 1 && bytes[i] == 0) {
            i++;
        }
        if (i == 0) return bytes;
        return Arrays.copyOfRange(bytes, i, bytes.length);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"kty\":\"").append(kty).append("\"");
        if (kid != null) sb.append(",\"kid\":\"").append(escape(kid)).append("\"");
        if (use != null) sb.append(",\"use\":\"").append(escape(use)).append("\"");
        if (alg != null) sb.append(",\"alg\":\"").append(escape(alg)).append("\"");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(",\"").append(escape(entry.getKey())).append("\":\"")
              .append(escape(entry.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JwkKey jwkKey)) return false;
        return Objects.equals(kid, jwkKey.kid) &&
               Objects.equals(kty, jwkKey.kty) &&
               Objects.equals(use, jwkKey.use) &&
               Objects.equals(alg, jwkKey.alg) &&
               Objects.equals(params, jwkKey.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kid, kty, use, alg, params);
    }

    @Override
    public String toString() {
        return "JwkKey{kid='" + kid + "', kty='" + kty + "', use='" + use + "', alg='" + alg + "'}";
    }
}
