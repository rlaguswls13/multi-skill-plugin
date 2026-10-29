package com.msp.pwd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Pbkdf2PasswordHasherTest {

    @Test
    @DisplayName("Hash format conforms to $pbkdf2$<iterations>$<salt>$<hash>")
    void testHashFormat() {
        PasswordHasher hasher = new Pbkdf2PasswordHasher(65536);
        String hash = hasher.hash("SecurePassword123!");

        assertNotNull(hash);
        assertTrue(hash.startsWith("$pbkdf2$65536$"));

        String[] parts = hash.split("\\$");
        assertEquals(5, parts.length);
        assertEquals("pbkdf2", parts[1]);
        assertEquals("65536", parts[2]);
        assertFalse(parts[3].isBlank()); // salt
        assertFalse(parts[4].isBlank()); // digest
    }

    @Test
    @DisplayName("Verify returns true for correct password and false for incorrect password")
    void testVerifyPositiveAndNegative() {
        PasswordHasher hasher = new Pbkdf2PasswordHasher(10_000); // 10k for fast unit test
        String rawPassword = "CorrectHorseBatteryStaple";

        String encodedHash = hasher.hash(rawPassword);

        assertTrue(hasher.verify(rawPassword, encodedHash));
        assertFalse(hasher.verify("WrongPassword", encodedHash));
        assertFalse(hasher.verify(rawPassword.toLowerCase(), encodedHash));
    }

    @Test
    @DisplayName("Two hashes of identical password must differ due to random salt")
    void testSaltRandomness() {
        PasswordHasher hasher = new Pbkdf2PasswordHasher(10_000);
        String password = "SamePasswordEveryTime";

        String hash1 = hasher.hash(password);
        String hash2 = hasher.hash(password);

        assertNotEquals(hash1, hash2);
        assertTrue(hasher.verify(password, hash1));
        assertTrue(hasher.verify(password, hash2));
    }

    @Test
    @DisplayName("Tampered hash components must safely fail verification")
    void testTamperedHash() {
        PasswordHasher hasher = new Pbkdf2PasswordHasher(10_000);
        String password = "MySecretPassword";
        String validHash = hasher.hash(password);

        String[] parts = validHash.split("\\$");
        // Tamper with digest
        String tamperedDigest = parts[4].substring(0, parts[4].length() - 1) + (parts[4].endsWith("A") ? "B" : "A");
        String tamperedHash = "$" + parts[1] + "$" + parts[2] + "$" + parts[3] + "$" + tamperedDigest;

        assertFalse(hasher.verify(password, tamperedHash));
    }

    @Test
    @DisplayName("Malformed hash strings must safely return false without throwing exceptions")
    void testMalformedHashStrings() {
        PasswordHasher hasher = new Pbkdf2PasswordHasher(10_000);

        assertFalse(hasher.verify("pass", null));
        assertFalse(hasher.verify(null, "$pbkdf2$1000$salt$hash"));
        assertFalse(hasher.verify("pass", ""));
        assertFalse(hasher.verify("pass", "plain-text-not-a-hash"));
        assertFalse(hasher.verify("pass", "$bcrypt$12$salt$hash"));
        assertFalse(hasher.verify("pass", "$pbkdf2$not_a_number$salt$hash"));
        assertFalse(hasher.verify("pass", "$pbkdf2$1000$not_valid_base64!@#$hash"));
    }
}
