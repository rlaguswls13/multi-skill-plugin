package com.msp.jwelite;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Objects;

/**
 * Representation of an RFC 7516 JSON Web Encryption (JWE) token with Compact Serialization support.
 */
public final class JweToken {

    private final JweHeader header;
    private final byte[] encryptedKey;
    private final byte[] iv;
    private final byte[] ciphertext;
    private final byte[] authTag;

    public JweToken(JweHeader header, byte[] encryptedKey, byte[] iv, byte[] ciphertext, byte[] authTag) {
        this.header = Objects.requireNonNull(header, "header must not be null");
        this.encryptedKey = encryptedKey != null ? encryptedKey.clone() : new byte[0];
        this.iv = Objects.requireNonNull(iv, "iv must not be null").clone();
        this.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext must not be null").clone();
        this.authTag = Objects.requireNonNull(authTag, "authTag must not be null").clone();
    }

    public JweHeader getHeader() {
        return header;
    }

    public byte[] getEncryptedKey() {
        return encryptedKey.clone();
    }

    public byte[] getIv() {
        return iv.clone();
    }

    public byte[] getCiphertext() {
        return ciphertext.clone();
    }

    public byte[] getAuthTag() {
        return authTag.clone();
    }

    /**
     * Serializes this JWE token into RFC 7516 Compact format (5 Base64URL dot-separated segments).
     */
    public String toCompact() {
        return header.toBase64Url() + "." +
                JweKeyUtils.base64UrlEncode(encryptedKey) + "." +
                JweKeyUtils.base64UrlEncode(iv) + "." +
                JweKeyUtils.base64UrlEncode(ciphertext) + "." +
                JweKeyUtils.base64UrlEncode(authTag);
    }

    @Override
    public String toString() {
        return toCompact();
    }

    /**
     * Parses an RFC 7516 Compact JWE string into a JweToken.
     */
    public static JweToken parse(String compactJwe) {
        if (compactJwe == null || compactJwe.isBlank()) {
            throw new JweException("Compact JWE string must not be null or blank");
        }
        String[] parts = compactJwe.trim().split("\\.", -1);
        if (parts.length != 5) {
            throw new JweException("Invalid JWE Compact Serialization: expected 5 parts separated by '.', got " + parts.length);
        }

        JweHeader header = JweHeader.fromBase64Url(parts[0]);
        byte[] encryptedKey = JweKeyUtils.base64UrlDecode(parts[1]);
        byte[] iv = JweKeyUtils.base64UrlDecode(parts[2]);
        byte[] ciphertext = JweKeyUtils.base64UrlDecode(parts[3]);
        byte[] authTag = JweKeyUtils.base64UrlDecode(parts[4]);

        return new JweToken(header, encryptedKey, iv, ciphertext, authTag);
    }

    /**
     * Decrypts the JWE payload using the provided private RSA key or shared AES SecretKey.
     */
    public byte[] decrypt(Key key) {
        Objects.requireNonNull(key, "decryption key must not be null");

        // 1. Unwrap or obtain CEK
        SecretKey cek = JweCrypto.unwrapKey(header.alg(), encryptedKey, key, header.enc());

        // 2. Compute AAD (ASCII bytes of protected header base64url string)
        byte[] aad = header.toBase64Url().getBytes(StandardCharsets.US_ASCII);

        // 3. Decrypt ciphertext with AES-GCM and verify auth tag
        return JweCrypto.decryptContent(header.enc(), cek, iv, ciphertext, authTag, aad);
    }

    /**
     * Decrypts the JWE payload and converts it to a UTF-8 string.
     */
    public String decryptToString(Key key) {
        byte[] decrypted = decrypt(key);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String alg = JweAlgorithm.RSA_OAEP_256;
        private String enc = JweEncryption.A256GCM;
        private String cty;
        private String kid;
        private String typ;
        private byte[] payload;

        private Builder() {
        }

        public Builder algorithm(String alg) {
            this.alg = alg;
            return this;
        }

        public Builder encryption(String enc) {
            this.enc = enc;
            return this;
        }

        public Builder contentType(String cty) {
            this.cty = cty;
            return this;
        }

        public Builder keyId(String kid) {
            this.kid = kid;
            return this;
        }

        public Builder type(String typ) {
            this.typ = typ;
            return this;
        }

        public Builder payload(byte[] payload) {
            this.payload = payload != null ? payload.clone() : null;
            return this;
        }

        public Builder payload(String payload) {
            this.payload = payload != null ? payload.getBytes(StandardCharsets.UTF_8) : null;
            return this;
        }

        public JweToken encrypt(Key recipientKey) {
            Objects.requireNonNull(recipientKey, "recipientKey must not be null");
            if (payload == null) {
                throw new JweException("Payload must be specified before encrypting");
            }

            JweHeader header = new JweHeader(alg, enc, cty, kid, typ);
            int cekBits = JweEncryption.getKeyLengthInBytes(enc) * 8;

            SecretKey cek;
            if (JweAlgorithm.DIR.equals(alg)) {
                if (!(recipientKey instanceof SecretKey secretKey)) {
                    throw new JweException("Direct encryption ('dir') requires a SecretKey");
                }
                cek = secretKey;
            } else {
                cek = JweKeyUtils.generateAesKey(cekBits);
            }

            byte[] encryptedKey = JweCrypto.wrapKey(alg, cek, recipientKey);
            byte[] aad = header.toBase64Url().getBytes(StandardCharsets.US_ASCII);
            JweCrypto.EncryptedContent content = JweCrypto.encryptContent(enc, cek, payload, aad);

            return new JweToken(header, encryptedKey, content.iv(), content.ciphertext(), content.authTag());
        }
    }
}
