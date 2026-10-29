package com.msp.crypto;

import javax.crypto.SecretKey;

/**
 * Unified facade providing one-stop access to AES-GCM, PBKDF2, HMAC, and SHA hashing.
 */
public final class CryptoLite {

    private CryptoLite() {
        // Utility class
    }

    // --- AES-256-GCM ---

    public static SecretKey generateAesKey() {
        return AesGcmCrypto.generateKey();
    }

    public static SecretKey aesKeyFromBytes(byte[] keyBytes) {
        return AesGcmCrypto.keyFromBytes(keyBytes);
    }

    public static SecretKey aesKeyFromBase64(String base64Key) {
        return AesGcmCrypto.keyFromBase64(base64Key);
    }

    public static byte[] encryptAesGcm(byte[] plaintext, SecretKey key) {
        return AesGcmCrypto.encrypt(plaintext, key);
    }

    public static byte[] encryptAesGcm(byte[] plaintext, SecretKey key, byte[] aad) {
        return AesGcmCrypto.encrypt(plaintext, key, aad);
    }

    public static byte[] decryptAesGcm(byte[] ciphertextWithIv, SecretKey key) {
        return AesGcmCrypto.decrypt(ciphertextWithIv, key);
    }

    public static byte[] decryptAesGcm(byte[] ciphertextWithIv, SecretKey key, byte[] aad) {
        return AesGcmCrypto.decrypt(ciphertextWithIv, key, aad);
    }

    public static String encryptAesGcm(String plaintext, SecretKey key) {
        return AesGcmCrypto.encryptToBase64(plaintext, key);
    }

    public static String encryptAesGcm(String plaintext, SecretKey key, String aad) {
        return AesGcmCrypto.encryptToBase64(plaintext, key, aad);
    }

    public static String decryptAesGcm(String base64Ciphertext, SecretKey key) {
        return AesGcmCrypto.decryptFromBase64(base64Ciphertext, key);
    }

    public static String decryptAesGcm(String base64Ciphertext, SecretKey key, String aad) {
        return AesGcmCrypto.decryptFromBase64(base64Ciphertext, key, aad);
    }

    // --- PBKDF2 Key Derivation ---

    public static byte[] generateSalt(int lengthBytes) {
        return Pbkdf2KeyDerivation.generateSalt(lengthBytes);
    }

    public static byte[] generateSalt() {
        return Pbkdf2KeyDerivation.generateSalt();
    }

    public static SecretKey deriveAesKey(String password, byte[] salt) {
        return Pbkdf2KeyDerivation.deriveAesKey(password, salt);
    }

    public static SecretKey deriveAesKey(char[] password, byte[] salt) {
        return Pbkdf2KeyDerivation.deriveAesKey(password, salt);
    }

    public static SecretKey deriveAesKey(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        return Pbkdf2KeyDerivation.deriveAesKey(password, salt, iterations, keyLengthBits);
    }

    // --- HMAC-SHA256 ---

    public static String signHmacHex(String data, String key) {
        return HmacSigner.signHex(data, key);
    }

    public static boolean verifyHmacHex(String data, String signatureHex, String key) {
        return HmacSigner.verifyHex(data, signatureHex, key);
    }

    public static byte[] signHmac(byte[] data, byte[] key) {
        return HmacSigner.sign(data, key);
    }

    public static boolean verifyHmac(byte[] data, byte[] signature, byte[] key) {
        return HmacSigner.verify(data, signature, key);
    }

    // --- SHA Hashing ---

    public static String sha256Hex(String text) {
        return HashUtil.sha256Hex(text);
    }

    public static byte[] sha256(byte[] data) {
        return HashUtil.sha256(data);
    }

    public static String sha512Hex(String text) {
        return HashUtil.sha512Hex(text);
    }

    public static byte[] sha512(byte[] data) {
        return HashUtil.sha512(data);
    }
}
