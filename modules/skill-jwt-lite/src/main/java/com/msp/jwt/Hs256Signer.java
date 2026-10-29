package com.msp.jwt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * HS256 (HMAC-SHA256) JWT signer and verifier.
 */
public final class Hs256Signer implements JwtSigner {

    private static final String MAC_ALGORITHM = "HmacSHA256";
    private final byte[] secretKey;

    public Hs256Signer(byte[] secretKey) {
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey must not be null").clone();
    }

    public Hs256Signer(String secretKey) {
        this(Objects.requireNonNull(secretKey, "secretKey must not be null").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String algorithm() {
        return "HS256";
    }

    @Override
    public byte[] sign(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, MAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new JwtException("Failed to generate HS256 signature", e);
        }
    }

    @Override
    public boolean verify(byte[] data, byte[] signature) {
        if (data == null || signature == null) {
            return false;
        }
        try {
            byte[] expected = sign(data);
            return MessageDigest.isEqual(expected, signature);
        } catch (Exception e) {
            return false;
        }
    }
}
