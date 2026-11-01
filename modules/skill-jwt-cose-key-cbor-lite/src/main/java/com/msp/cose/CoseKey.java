package com.msp.cose;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.*;

/**
 * RFC 8152 / RFC 9052 COSE Key parser and representation.
 * Supports WebAuthn, Passkeys, and FIDO2 public keys (EC2, RSA, OKP).
 */
public final class CoseKey {

    // Common COSE Key Parameters
    public static final long LABEL_KTY = 1L;
    public static final long LABEL_KID = 2L;
    public static final long LABEL_ALG = 3L;
    public static final long LABEL_KEY_OPS = 4L;
    public static final long LABEL_BASE_IV = 5L;

    // EC2 & OKP Parameters
    public static final long LABEL_CRV = -1L;
    public static final long LABEL_X = -2L;
    public static final long LABEL_Y = -3L;
    public static final long LABEL_D = -4L;

    // RSA Parameters
    public static final long LABEL_RSA_N = -1L;
    public static final long LABEL_RSA_E = -2L;
    public static final long LABEL_RSA_D = -3L;

    // Key Types (kty)
    public static final int KTY_OKP = 1;
    public static final int KTY_EC2 = 2;
    public static final int KTY_RSA = 3;
    public static final int KTY_SYMMETRIC = 4;

    // Algorithms (alg)
    public static final int ALG_ES256 = -7;
    public static final int ALG_EDDSA = -8;
    public static final int ALG_ES384 = -35;
    public static final int ALG_ES512 = -36;
    public static final int ALG_RS256 = -257;
    public static final int ALG_RS384 = -258;
    public static final int ALG_RS512 = -259;
    public static final int ALG_PS256 = -37;

    // Curves (crv)
    public static final int CRV_P256 = 1;
    public static final int CRV_P384 = 2;
    public static final int CRV_P521 = 3;
    public static final int CRV_X25519 = 4;
    public static final int CRV_ED25519 = 6;
    public static final int CRV_SECP256K1 = 8;

    private static final byte[] ED25519_X509_PREFIX = new byte[] {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private final Map<Long, CborValue> parameters;

    private CoseKey(Map<Long, CborValue> parameters) {
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    public static CoseKey parse(byte[] cborBytes) {
        CborValue val = CborReader.read(cborBytes);
        if (!(val instanceof CborValue.MapVal mapVal)) {
            throw new CborParseException("Expected CBOR Map for COSE Key, got: " + val.getClass().getSimpleName());
        }

        Map<Long, CborValue> params = new LinkedHashMap<>();
        for (Map.Entry<CborValue, CborValue> entry : mapVal.entries().entrySet()) {
            if (entry.getKey() instanceof CborValue.IntegerVal(long keyNum)) {
                params.put(keyNum, entry.getValue());
            }
        }
        return new CoseKey(params);
    }

    public static CoseKey parseHex(String hex) {
        return parse(CborReader.hexToBytes(hex));
    }

    public static CoseKey of(Map<Long, CborValue> params) {
        return new CoseKey(params);
    }

    public Optional<Long> getKeyType() {
        return getInteger(LABEL_KTY);
    }

    public Optional<Long> getAlgorithm() {
        return getInteger(LABEL_ALG);
    }

    public Optional<byte[]> getKeyId() {
        return getByteString(LABEL_KID);
    }

    public Optional<Long> getCurve() {
        return getInteger(LABEL_CRV);
    }

    public Optional<byte[]> getXCoordinate() {
        return getByteString(LABEL_X);
    }

    public Optional<byte[]> getYCoordinate() {
        return getByteString(LABEL_Y);
    }

    public Optional<byte[]> getRsaModulus() {
        return getByteString(LABEL_RSA_N);
    }

    public Optional<byte[]> getRsaExponent() {
        return getByteString(LABEL_RSA_E);
    }

    public Optional<CborValue> getParameter(long label) {
        return Optional.ofNullable(parameters.get(label));
    }

    public Optional<Long> getInteger(long label) {
        CborValue val = parameters.get(label);
        if (val instanceof CborValue.IntegerVal(long num)) {
            return Optional.of(num);
        }
        return Optional.empty();
    }

    public Optional<byte[]> getByteString(long label) {
        CborValue val = parameters.get(label);
        if (val instanceof CborValue.ByteStringVal(byte[] bytes)) {
            return Optional.of(bytes);
        }
        return Optional.empty();
    }

    public Map<Long, CborValue> getParameters() {
        return parameters;
    }

    public byte[] encode() {
        Map<CborValue, CborValue> cborMap = new LinkedHashMap<>();
        for (Map.Entry<Long, CborValue> entry : parameters.entrySet()) {
            cborMap.put(new CborValue.IntegerVal(entry.getKey()), entry.getValue());
        }
        return CborWriter.encode(new CborValue.MapVal(cborMap));
    }

    /**
     * Converts this COSE Key to a standard Java {@link PublicKey}.
     * Supports EC2 (P-256, P-384, P-521, secp256k1), RSA, and OKP (Ed25519).
     */
    public PublicKey toPublicKey() throws GeneralSecurityException {
        long kty = getKeyType().orElseThrow(() -> new InvalidKeySpecException("Missing kty (label 1) in COSE key"));

        if (kty == KTY_EC2) {
            return toEcPublicKey();
        } else if (kty == KTY_RSA) {
            return toRsaPublicKey();
        } else if (kty == KTY_OKP) {
            return toOkpPublicKey();
        } else {
            throw new InvalidKeySpecException("Unsupported kty: " + kty);
        }
    }

    public ECPublicKey toEcPublicKey() throws GeneralSecurityException {
        long crv = getCurve().orElseThrow(() -> new InvalidKeySpecException("Missing crv (label -1) for EC2 key"));
        byte[] x = getXCoordinate().orElseThrow(() -> new InvalidKeySpecException("Missing x (label -2) for EC2 key"));
        byte[] y = getYCoordinate().orElseThrow(() -> new InvalidKeySpecException("Missing y (label -3) for EC2 key"));

        String curveName = switch ((int) crv) {
            case CRV_P256 -> "secp256r1";
            case CRV_P384 -> "secp384r1";
            case CRV_P521 -> "secp521r1";
            case CRV_SECP256K1 -> "secp256k1";
            default -> throw new InvalidKeySpecException("Unsupported EC curve ID: " + crv);
        };

        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec(curveName));
        ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(point, ecSpec);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return (ECPublicKey) kf.generatePublic(pubKeySpec);
    }

    public RSAPublicKey toRsaPublicKey() throws GeneralSecurityException {
        byte[] n = getRsaModulus().orElseThrow(() -> new InvalidKeySpecException("Missing n (label -1) for RSA key"));
        byte[] e = getRsaExponent().orElseThrow(() -> new InvalidKeySpecException("Missing e (label -2) for RSA key"));

        BigInteger modulus = new BigInteger(1, n);
        BigInteger exponent = new BigInteger(1, e);

        RSAPublicKeySpec rsaSpec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(rsaSpec);
    }

    public PublicKey toOkpPublicKey() throws GeneralSecurityException {
        long crv = getCurve().orElseThrow(() -> new InvalidKeySpecException("Missing crv (label -1) for OKP key"));
        byte[] x = getXCoordinate().orElseThrow(() -> new InvalidKeySpecException("Missing x (label -2) for OKP key"));

        if (crv == CRV_ED25519) {
            if (x.length != 32) {
                throw new InvalidKeySpecException("Ed25519 public key x coordinate must be 32 bytes, got " + x.length);
            }
            byte[] x509Bytes = new byte[ED25519_X509_PREFIX.length + 32];
            System.arraycopy(ED25519_X509_PREFIX, 0, x509Bytes, 0, ED25519_X509_PREFIX.length);
            System.arraycopy(x, 0, x509Bytes, ED25519_X509_PREFIX.length, 32);

            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePublic(new X509EncodedKeySpec(x509Bytes));
        }

        throw new InvalidKeySpecException("Unsupported OKP curve: " + crv);
    }

    @Override
    public String toString() {
        return "CoseKey{" +
                "kty=" + getKeyType().orElse(null) +
                ", alg=" + getAlgorithm().orElse(null) +
                ", crv=" + getCurve().orElse(null) +
                ", params=" + parameters.keySet() +
                '}';
    }
}
