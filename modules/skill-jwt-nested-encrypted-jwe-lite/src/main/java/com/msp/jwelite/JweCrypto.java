package com.msp.jwelite;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Objects;

/**
 * Core cryptographic routines for RFC 7516 JSON Web Encryption.
 */
public final class JweCrypto {

    public record EncryptedContent(byte[] iv, byte[] ciphertext, byte[] authTag) {
    }

    private JweCrypto() {
    }

    public static byte[] wrapKey(String alg, SecretKey cek, Key key) {
        if (JweAlgorithm.DIR.equals(alg)) {
            return new byte[0];
        }
        if (!(key instanceof PublicKey publicKey)) {
            throw new JweException("Algorithm " + alg + " requires an RSA PublicKey for encryption");
        }
        try {
            OAEPParameterSpec spec = createOaepSpec(alg);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec);
            return cipher.doFinal(cek.getEncoded());
        } catch (Exception e) {
            throw new JweException("Failed to wrap CEK with " + alg, e);
        }
    }

    public static SecretKey unwrapKey(String alg, byte[] encryptedKey, Key key, String enc) {
        if (JweAlgorithm.DIR.equals(alg)) {
            if (!(key instanceof SecretKey secretKey)) {
                throw new JweException("Algorithm 'dir' requires a SecretKey for decryption");
            }
            if (encryptedKey != null && encryptedKey.length > 0) {
                throw new JweException("Encrypted key must be empty when alg is 'dir'");
            }
            return secretKey;
        }

        if (!(key instanceof PrivateKey privateKey)) {
            throw new JweException("Algorithm " + alg + " requires an RSA PrivateKey for decryption");
        }

        try {
            OAEPParameterSpec spec = createOaepSpec(alg);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey, spec);
            byte[] rawCek = cipher.doFinal(encryptedKey);
            return new SecretKeySpec(rawCek, "AES");
        } catch (Exception e) {
            throw new JweException("Failed to unwrap CEK with " + alg, e);
        }
    }

    public static EncryptedContent encryptContent(String enc, SecretKey cek, byte[] plaintext, byte[] aad) {
        Objects.requireNonNull(enc, "enc must not be null");
        Objects.requireNonNull(cek, "cek must not be null");
        Objects.requireNonNull(plaintext, "plaintext must not be null");

        byte[] iv = JweKeyUtils.randomBytes(12); // Standard 96-bit IV for AES-GCM
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, cek, spec);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            byte[] cipherWithTag = cipher.doFinal(plaintext);

            // Java AES/GCM appends 16-byte (128-bit) tag at the end
            int tagLength = 16;
            int cipherLen = cipherWithTag.length - tagLength;
            byte[] ciphertext = Arrays.copyOfRange(cipherWithTag, 0, cipherLen);
            byte[] authTag = Arrays.copyOfRange(cipherWithTag, cipherLen, cipherWithTag.length);

            return new EncryptedContent(iv, ciphertext, authTag);
        } catch (Exception e) {
            throw new JweException("AES-GCM content encryption failed (" + enc + ")", e);
        }
    }

    public static byte[] decryptContent(String enc, SecretKey cek, byte[] iv, byte[] ciphertext, byte[] authTag, byte[] aad) {
        Objects.requireNonNull(enc, "enc must not be null");
        Objects.requireNonNull(cek, "cek must not be null");
        Objects.requireNonNull(iv, "iv must not be null");
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        Objects.requireNonNull(authTag, "authTag must not be null");

        byte[] cipherWithTag = new byte[ciphertext.length + authTag.length];
        System.arraycopy(ciphertext, 0, cipherWithTag, 0, ciphertext.length);
        System.arraycopy(authTag, 0, cipherWithTag, ciphertext.length, authTag.length);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, cek, spec);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(cipherWithTag);
        } catch (Exception e) {
            throw new JweException("AES-GCM content decryption or authentication tag verification failed", e);
        }
    }

    private static OAEPParameterSpec createOaepSpec(String alg) {
        if (JweAlgorithm.RSA_OAEP.equals(alg)) {
            return new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
        } else if (JweAlgorithm.RSA_OAEP_256.equals(alg)) {
            return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        }
        throw new JweException("Unsupported RSA OAEP algorithm: " + alg);
    }
}
