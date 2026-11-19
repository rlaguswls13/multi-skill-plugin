package com.msp.dpop;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Pure Java 21 Cryptographic utility for DPoP JWK parsing, thumbprint calculation (RFC 7638),
 * and digital signature verification (RS256, ES256).
 */
public final class DpopKeyUtil {

    private DpopKeyUtil() {
    }

    /**
     * Parses a JWK Map into a standard Java PublicKey (RSA or EC P-256).
     */
    public static PublicKey parseJwk(Map<String, Object> jwk) throws GeneralSecurityException {
        String kty = (String) jwk.get("kty");
        if (kty == null) {
            throw new InvalidKeyException("Missing 'kty' in JWK");
        }

        // Must NOT contain private key elements (RFC 9449 §4.2)
        if (jwk.containsKey("d") || jwk.containsKey("p") || jwk.containsKey("q") ||
                jwk.containsKey("dp") || jwk.containsKey("dq") || jwk.containsKey("qi")) {
            throw new InvalidKeyException("JWK must not contain private key parameters");
        }

        if ("RSA".equalsIgnoreCase(kty)) {
            String nStr = (String) jwk.get("n");
            String eStr = (String) jwk.get("e");
            if (nStr == null || eStr == null) {
                throw new InvalidKeyException("Missing 'n' or 'e' parameter in RSA JWK");
            }
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(nStr));
            BigInteger publicExponent = new BigInteger(1, Base64.getUrlDecoder().decode(eStr));
            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, publicExponent);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } else if ("EC".equalsIgnoreCase(kty)) {
            String crv = (String) jwk.get("crv");
            String xStr = (String) jwk.get("x");
            String yStr = (String) jwk.get("y");

            if (!"P-256".equalsIgnoreCase(crv)) {
                throw new InvalidKeyException("Unsupported EC curve: " + crv + " (only P-256 is supported for ES256)");
            }
            if (xStr == null || yStr == null) {
                throw new InvalidKeyException("Missing 'x' or 'y' coordinate in EC JWK");
            }

            BigInteger x = new BigInteger(1, Base64.getUrlDecoder().decode(xStr));
            BigInteger y = new BigInteger(1, Base64.getUrlDecoder().decode(yStr));

            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

            ECPublicKeySpec spec = new ECPublicKeySpec(new ECPoint(x, y), ecSpec);
            return KeyFactory.getInstance("EC").generatePublic(spec);
        }

        throw new InvalidKeyException("Unsupported key type: " + kty);
    }

    /**
     * Calculates the RFC 7638 JWK Thumbprint (SHA-256 base64url-encoded).
     */
    public static String computeThumbprint(Map<String, Object> jwk) throws GeneralSecurityException {
        String kty = (String) jwk.get("kty");
        String canonicalJson;

        if ("RSA".equalsIgnoreCase(kty)) {
            String e = (String) jwk.get("e");
            String n = (String) jwk.get("n");
            canonicalJson = "{\"e\":\"" + e + "\",\"kty\":\"RSA\",\"n\":\"" + n + "\"}";
        } else if ("EC".equalsIgnoreCase(kty)) {
            String crv = (String) jwk.get("crv");
            String x = (String) jwk.get("x");
            String y = (String) jwk.get("y");
            canonicalJson = "{\"crv\":\"" + crv + "\",\"kty\":\"EC\",\"x\":\"" + x + "\",\"y\":\"" + y + "\"}";
        } else {
            throw new InvalidKeyException("Unsupported key type for thumbprint: " + kty);
        }

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /**
     * Computes the base64url-encoded SHA-256 hash of an access token (RFC 9449 §4.2 'ath').
     */
    public static String computeAccessTokenHash(String accessToken) {
        if (accessToken == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Verifies the cryptographic signature of the DPoP proof.
     */
    public static boolean verifySignature(
            String alg,
            PublicKey publicKey,
            byte[] signingInput,
            byte[] signatureBytes
    ) throws GeneralSecurityException {
        if ("RS256".equalsIgnoreCase(alg)) {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(signingInput);
            return sig.verify(signatureBytes);
        } else if ("ES256".equalsIgnoreCase(alg)) {
            // First try standard IEEE P1363 signature format (SunEC standard in Java 9+)
            try {
                Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
                sig.initVerify(publicKey);
                sig.update(signingInput);
                return sig.verify(signatureBytes);
            } catch (NoSuchAlgorithmException | SignatureException e) {
                // Fallback to transcoding to DER format
                byte[] derSignature = p1363ToDer(signatureBytes);
                Signature sig = Signature.getInstance("SHA256withECDSA");
                sig.initVerify(publicKey);
                sig.update(signingInput);
                return sig.verify(derSignature);
            }
        }
        throw new NoSuchAlgorithmException("Unsupported algorithm: " + alg);
    }

    /**
     * Converts a 64-byte IEEE P1363 raw signature (r || s) into standard ASN.1 DER format.
     */
    public static byte[] p1363ToDer(byte[] p1363) {
        if (p1363.length != 64) {
            return p1363; // Might already be DER
        }
        byte[] rBytes = new byte[32];
        byte[] sBytes = new byte[32];
        System.arraycopy(p1363, 0, rBytes, 0, 32);
        System.arraycopy(p1363, 32, sBytes, 0, 32);

        BigInteger r = new BigInteger(1, rBytes);
        BigInteger s = new BigInteger(1, sBytes);

        byte[] rDer = r.toByteArray();
        byte[] sDer = s.toByteArray();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x30); // SEQUENCE
        int len = 2 + rDer.length + 2 + sDer.length;
        baos.write(len);

        baos.write(0x02); // INTEGER
        baos.write(rDer.length);
        baos.write(rDer, 0, rDer.length);

        baos.write(0x02); // INTEGER
        baos.write(sDer.length);
        baos.write(sDer, 0, sDer.length);

        return baos.toByteArray();
    }
}
