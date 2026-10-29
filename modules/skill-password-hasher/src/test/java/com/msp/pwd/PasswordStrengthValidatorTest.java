package com.msp.pwd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordStrengthValidatorTest {

    @Test
    @DisplayName("Strong password passes all enterprise rules and achieves VERY_STRONG")
    void testStrongPassword() {
        PasswordStrengthValidator validator = PasswordStrengthValidator.strong();
        PasswordStrengthResult result = validator.validate("P@ssw0rdSecure2026!");

        assertTrue(result.isValid());
        assertTrue(result.violations().isEmpty());
        assertTrue(result.score() >= 80);
        assertEquals(StrengthLevel.VERY_STRONG, result.level());
    }

    @Test
    @DisplayName("Short password violates minimum length")
    void testShortPassword() {
        PasswordStrengthValidator validator = PasswordStrengthValidator.strong();
        PasswordStrengthResult result = validator.validate("Ab1!");

        assertFalse(result.isValid());
        assertFalse(result.violations().isEmpty());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("at least 10 characters")));
    }

    @Test
    @DisplayName("Missing character classes detected")
    void testMissingCharacterClasses() {
        PasswordStrengthValidator validator = PasswordStrengthValidator.strong();

        // Missing uppercase
        PasswordStrengthResult noUpper = validator.validate("p@ssw0rd12345");
        assertFalse(noUpper.isValid());
        assertTrue(noUpper.violations().stream().anyMatch(v -> v.contains("uppercase letter")));

        // Missing lowercase
        PasswordStrengthResult noLower = validator.validate("P@SSW0RD12345");
        assertFalse(noLower.isValid());
        assertTrue(noLower.violations().stream().anyMatch(v -> v.contains("lowercase letter")));

        // Missing digit
        PasswordStrengthResult noDigit = validator.validate("P@sswordSecure!");
        assertFalse(noDigit.isValid());
        assertTrue(noDigit.violations().stream().anyMatch(v -> v.contains("digit")));

        // Missing special char
        PasswordStrengthResult noSpecial = validator.validate("Password123456");
        assertFalse(noSpecial.isValid());
        assertTrue(noSpecial.violations().stream().anyMatch(v -> v.contains("special character")));
    }

    @Test
    @DisplayName("Whitespace detection in passwords")
    void testWhitespaceDisallowed() {
        PasswordStrengthValidator validator = PasswordStrengthValidator.strong();
        PasswordStrengthResult result = validator.validate("P@ss word 12345");

        assertFalse(result.isValid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("whitespace")));
    }

    @Test
    @DisplayName("Lenient preset allows simpler passwords")
    void testLenientPreset() {
        PasswordStrengthValidator validator = PasswordStrengthValidator.lenient();
        PasswordStrengthResult result = validator.validate("simple");

        assertTrue(result.isValid());
        assertTrue(result.violations().isEmpty());
    }
}
