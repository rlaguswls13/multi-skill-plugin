package com.msp.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

class Pbkdf2KeyDerivationTest {

    @Test
    @DisplayName("PBKDF2 derivation should be deterministic given same password, salt, and iterations")
    void testDeterministicDerivation() {
        String password = "Master-Super-Secret-Password-2026";
        byte[] salt = Pbkdf2KeyDerivation.generateSalt(16);

        SecretKey key1 = Pbkdf2KeyDerivation.deriveAesKey(password, salt);
        SecretKey key2 = Pbkdf2KeyDerivation.deriveAesKey(password, salt);

        assertArrayEquals(key1.getEncoded(), key2.getEncoded());
        assertEquals(32, key1.getEncoded().length); // 256 bits
    }

    @Test
    @DisplayName("Different salts or passwords must produce distinct derived keys")
    void testDifferentParametersProduceDifferentKeys() {
        byte[] salt1 = Pbkdf2KeyDerivation.generateSalt();
        byte[] salt2 = Pbkdf2KeyDerivation.generateSalt();

        SecretKey keyA = Pbkdf2KeyDerivation.deriveAesKey("PasswordA", salt1);
        SecretKey keyB = Pbkdf2KeyDerivation.deriveAesKey("PasswordB", salt1);
        SecretKey keyC = Pbkdf2KeyDerivation.deriveAesKey("PasswordA", salt2);

        assertFalse(java.util.Arrays.equals(keyA.getEncoded(), keyB.getEncoded()));
        assertFalse(java.util.Arrays.equals(keyA.getEncoded(), keyC.getEncoded()));
    }

    @Test
    @DisplayName("Derived key can immediately be used for AES-GCM encryption/decryption")
    void testIntegrationWithAesGcm() {
        String pass = "UserProvidedMasterKey";
        byte[] salt = Pbkdf2KeyDerivation.generateSalt();

        SecretKey derivedKey = Pbkdf2KeyDerivation.deriveAesKey(pass, salt);

        String message = "Encrypted using PBKDF2 derived AES key";
        String cipher = AesGcmCrypto.encryptToBase64(message, derivedKey);
        String decrypted = AesGcmCrypto.decryptFromBase64(cipher, derivedKey);

        assertEquals(message, decrypted);
    }

    @Test
    @DisplayName("Invalid parameters must throw appropriate exceptions")
    void testInvalidParameters() {
        byte[] salt = Pbkdf2KeyDerivation.generateSalt();
        assertThrows(NullPointerException.class, () -> Pbkdf2KeyDerivation.deriveAesKey((String) null, salt));
        assertThrows(NullPointerException.class, () -> Pbkdf2KeyDerivation.deriveAesKey("pass", null));
        assertThrows(IllegalArgumentException.class, () -> Pbkdf2KeyDerivation.generateSalt(0));
        assertThrows(IllegalArgumentException.class, () ->
                Pbkdf2KeyDerivation.deriveKey("pass".toCharArray(), salt, 0, 256)
        );
        assertThrows(IllegalArgumentException.class, () ->
                Pbkdf2KeyDerivation.deriveKey("pass".toCharArray(), salt, 1000, 0)
        );
    }
}
