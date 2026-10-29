package com.msp.jwt;

import java.security.*;
import java.util.Objects;

/**
 * RS256 (RSA with SHA-256) asymmetric JWT signer and verifier.
 */
public final class Rs256Signer implements JwtSigner {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public Rs256Signer(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        if (privateKey == null && publicKey == null) {
            throw new IllegalArgumentException("At least one of privateKey or publicKey must be provided");
        }
    }

    public Rs256Signer(PrivateKey privateKey) {
        this(Objects.requireNonNull(privateKey, "privateKey must not be null"), null);
    }

    public Rs256Signer(PublicKey publicKey) {
        this(null, Objects.requireNonNull(publicKey, "publicKey must not be null"));
    }

    @Override
    public String algorithm() {
        return "RS256";
    }

    @Override
    public byte[] sign(byte[] data) {
        if (privateKey == null) {
            throw new JwtException("RS256 cannot sign: PrivateKey not configured");
        }
        Objects.requireNonNull(data, "data must not be null");
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new JwtException("Failed to generate RS256 signature", e);
        }
    }

    @Override
    public boolean verify(byte[] data, byte[] signatureBytes) {
        if (publicKey == null) {
            throw new JwtException("RS256 cannot verify: PublicKey not configured");
        }
        if (data == null || signatureBytes == null) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            return false;
        }
    }
}
