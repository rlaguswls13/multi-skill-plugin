package com.msp.pkcs8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java PEM PKCS#8 & PKCS#1 private/public RSA key loader and exporter.
 * Zero external dependencies; uses JDK standard java.security and pure ASN.1 DER decoding.
 */
public final class PemKeyLoader {

    private static final String PKCS8_PRIVATE_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_PRIVATE_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PKCS1_PRIVATE_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PKCS1_PRIVATE_FOOTER = "-----END RSA PRIVATE KEY-----";

    private static final String X509_PUBLIC_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String X509_PUBLIC_FOOTER = "-----END PUBLIC KEY-----";
    private static final String PKCS1_PUBLIC_HEADER = "-----BEGIN RSA PUBLIC KEY-----";
    private static final String PKCS1_PUBLIC_FOOTER = "-----END RSA PUBLIC KEY-----";

    private static final Pattern PEM_PATTERN = Pattern.compile(
            "-----BEGIN ([A-Z0-9 ]+)-----\\s*([A-Za-z0-9+/=\\r\\n]+?)\\s*-----END \\1-----"
    );

    private PemKeyLoader() {
    }

    /**
     * Parses a PEM-encoded RSA Private Key (supports both PKCS#8 and PKCS#1).
     *
     * @param pem PEM string containing "-----BEGIN PRIVATE KEY-----" or "-----BEGIN RSA PRIVATE KEY-----"
     * @return RSAPrivateKey instance
     * @throws IllegalArgumentException if PEM format is invalid, corrupted, or unsupported
     */
    public static PrivateKey parsePrivateKey(String pem) {
        Objects.requireNonNull(pem, "PEM string cannot be null");
        String trimmed = pem.trim();

        Matcher matcher = PEM_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No valid PEM boundary found in private key input");
        }

        String type = matcher.group(1).trim();
        String base64Body = matcher.group(2).replaceAll("\\s+", "");
        byte[] derBytes;
        try {
            derBytes = Base64.getDecoder().decode(base64Body);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Corrupted Base64 body in private key PEM: " + e.getMessage(), e);
        }

        KeyFactory keyFactory = getRsaKeyFactory();

        try {
            if ("PRIVATE KEY".equals(type)) {
                // PKCS#8
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(derBytes);
                return keyFactory.generatePrivate(spec);
            } else if ("RSA PRIVATE KEY".equals(type)) {
                // PKCS#1
                return parsePkcs1PrivateKey(derBytes, keyFactory);
            } else {
                throw new IllegalArgumentException("Unsupported private key PEM header: -----BEGIN " + type + "-----");
            }
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to decode RSA private key: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a PEM-encoded RSA Public Key (supports X.509 SubjectPublicKeyInfo and PKCS#1 RSA Public Key).
     *
     * @param pem PEM string containing "-----BEGIN PUBLIC KEY-----" or "-----BEGIN RSA PUBLIC KEY-----"
     * @return PublicKey instance
     * @throws IllegalArgumentException if PEM format is invalid, corrupted, or unsupported
     */
    public static PublicKey parsePublicKey(String pem) {
        Objects.requireNonNull(pem, "PEM string cannot be null");
        String trimmed = pem.trim();

        Matcher matcher = PEM_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No valid PEM boundary found in public key input");
        }

        String type = matcher.group(1).trim();
        String base64Body = matcher.group(2).replaceAll("\\s+", "");
        byte[] derBytes;
        try {
            derBytes = Base64.getDecoder().decode(base64Body);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Corrupted Base64 body in public key PEM: " + e.getMessage(), e);
        }

        KeyFactory keyFactory = getRsaKeyFactory();

        try {
            if ("PUBLIC KEY".equals(type)) {
                // X.509 SubjectPublicKeyInfo
                X509EncodedKeySpec spec = new X509EncodedKeySpec(derBytes);
                return keyFactory.generatePublic(spec);
            } else if ("RSA PUBLIC KEY".equals(type)) {
                // PKCS#1 RSAPublicKey
                return parsePkcs1PublicKey(derBytes, keyFactory);
            } else {
                throw new IllegalArgumentException("Unsupported public key PEM header: -----BEGIN " + type + "-----");
            }
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to decode RSA public key: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to parse both private and public PEM keys into a KeyPair.
     */
    public static KeyPair parseKeyPair(String privatePem, String publicPem) {
        PrivateKey priv = parsePrivateKey(privatePem);
        PublicKey pub = parsePublicKey(publicPem);
        return new KeyPair(pub, priv);
    }

    /**
     * Converts a PrivateKey or PublicKey to standard PEM string format (PKCS#8 for private, X.509 for public).
     */
    public static String toPem(Key key) {
        Objects.requireNonNull(key, "Key cannot be null");

        String header;
        String footer;
        if (key instanceof PrivateKey) {
            header = PKCS8_PRIVATE_HEADER;
            footer = PKCS8_PRIVATE_FOOTER;
        } else if (key instanceof PublicKey) {
            header = X509_PUBLIC_HEADER;
            footer = X509_PUBLIC_FOOTER;
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + key.getClass().getName());
        }

        byte[] encoded = key.getEncoded();
        if (encoded == null) {
            throw new IllegalArgumentException("Key does not provide encoded DER format");
        }

        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return header + "\n" + base64 + "\n" + footer;
    }

    /**
     * Exports an RSAPrivateCrtKey into PKCS#1 PEM string format ("-----BEGIN RSA PRIVATE KEY-----").
     */
    public static String toPkcs1Pem(RSAPrivateCrtKey privateKey) {
        Objects.requireNonNull(privateKey, "RSA Private Key cannot be null");
        byte[] pkcs1Der = encodePkcs1Der(privateKey);
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pkcs1Der);
        return PKCS1_PRIVATE_HEADER + "\n" + base64 + "\n" + PKCS1_PRIVATE_FOOTER;
    }

    /**
     * Parses PKCS#1 ASN.1 RSAPrivateKey DER into PrivateKey.
     * ASN.1 RSAPrivateKey sequence:
     * version, modulus, publicExponent, privateExponent, prime1, prime2, exponent1, exponent2, coefficient
     */
    private static PrivateKey parsePkcs1PrivateKey(byte[] derBytes, KeyFactory keyFactory) throws InvalidKeySpecException {
        DerReader reader = new DerReader(derBytes);
        reader.readSequence();

        BigInteger version = reader.readInteger();
        if (version.intValue() != 0) {
            // Version 0 is two-prime RSA
            throw new IllegalArgumentException("Unsupported PKCS#1 RSAPrivateKey version: " + version);
        }

        BigInteger modulus = reader.readInteger();
        BigInteger publicExponent = reader.readInteger();
        BigInteger privateExponent = reader.readInteger();
        BigInteger prime1 = reader.readInteger();
        BigInteger prime2 = reader.readInteger();
        BigInteger exponent1 = reader.readInteger();
        BigInteger exponent2 = reader.readInteger();
        BigInteger coefficient = reader.readInteger();

        RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(
                modulus, publicExponent, privateExponent, prime1, prime2, exponent1, exponent2, coefficient
        );
        return keyFactory.generatePrivate(spec);
    }

    /**
     * Parses PKCS#1 ASN.1 RSAPublicKey DER into PublicKey.
     * ASN.1 RSAPublicKey sequence:
     * modulus, publicExponent
     */
    private static PublicKey parsePkcs1PublicKey(byte[] derBytes, KeyFactory keyFactory) throws InvalidKeySpecException {
        DerReader reader = new DerReader(derBytes);
        reader.readSequence();

        BigInteger modulus = reader.readInteger();
        BigInteger publicExponent = reader.readInteger();

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, publicExponent);
        return keyFactory.generatePublic(spec);
    }

    private static KeyFactory getRsaKeyFactory() {
        try {
            return KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not supported by JDK", e);
        }
    }

    /**
     * ASN.1 DER serializer for RSAPrivateCrtKey (PKCS#1).
     */
    private static byte[] encodePkcs1Der(RSAPrivateCrtKey key) {
        try {
            ByteArrayOutputStream seqBody = new ByteArrayOutputStream();
            writeDerInteger(seqBody, BigInteger.ZERO); // version 0
            writeDerInteger(seqBody, key.getModulus());
            writeDerInteger(seqBody, key.getPublicExponent());
            writeDerInteger(seqBody, key.getPrivateExponent());
            writeDerInteger(seqBody, key.getPrimeP());
            writeDerInteger(seqBody, key.getPrimeQ());
            writeDerInteger(seqBody, key.getPrimeExponentP());
            writeDerInteger(seqBody, key.getPrimeExponentQ());
            writeDerInteger(seqBody, key.getCrtCoefficient());

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            result.write(0x30); // SEQUENCE tag
            writeDerLength(result, seqBody.size());
            result.write(seqBody.toByteArray());
            return result.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode PKCS#1 DER: " + e.getMessage(), e);
        }
    }

    private static void writeDerInteger(ByteArrayOutputStream out, BigInteger val) throws IOException {
        out.write(0x02); // INTEGER tag
        byte[] bytes = val.toByteArray();
        writeDerLength(out, bytes.length);
        out.write(bytes);
    }

    private static void writeDerLength(ByteArrayOutputStream out, int length) {
        if (length < 128) {
            out.write(length);
        } else if (length <= 0xFF) {
            out.write(0x81);
            out.write(length);
        } else if (length <= 0xFFFF) {
            out.write(0x82);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        } else if (length <= 0xFFFFFF) {
            out.write(0x83);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(0x84);
            out.write((length >> 24) & 0xFF);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }

    /**
     * Simple ASN.1 DER Reader.
     */
    private static class DerReader {
        private final byte[] data;
        private int pos = 0;

        DerReader(byte[] data) {
            this.data = data;
        }

        int readTag() {
            if (pos >= data.length) {
                throw new IllegalArgumentException("Unexpected end of DER stream");
            }
            return data[pos++] & 0xFF;
        }

        int readLength() {
            if (pos >= data.length) {
                throw new IllegalArgumentException("Unexpected end of DER stream");
            }
            int b = data[pos++] & 0xFF;
            if ((b & 0x80) == 0) {
                return b;
            }
            int numBytes = b & 0x7F;
            if (numBytes > 4) {
                throw new IllegalArgumentException("DER length byte count exceeds supported range: " + numBytes);
            }
            int length = 0;
            for (int i = 0; i < numBytes; i++) {
                if (pos >= data.length) {
                    throw new IllegalArgumentException("Unexpected end of DER stream during length read");
                }
                length = (length << 8) | (data[pos++] & 0xFF);
            }
            return length;
        }

        void readSequence() {
            int tag = readTag();
            if (tag != 0x30) {
                throw new IllegalArgumentException("Expected ASN.1 SEQUENCE (0x30), got 0x" + Integer.toHexString(tag));
            }
            readLength();
        }

        BigInteger readInteger() {
            int tag = readTag();
            if (tag != 0x02) {
                throw new IllegalArgumentException("Expected ASN.1 INTEGER (0x02), got 0x" + Integer.toHexString(tag));
            }
            int length = readLength();
            if (pos + length > data.length) {
                throw new IllegalArgumentException("INTEGER payload length exceeds remaining buffer size");
            }
            byte[] bytes = Arrays.copyOfRange(data, pos, pos + length);
            pos += length;
            return new BigInteger(bytes);
        }
    }
}
