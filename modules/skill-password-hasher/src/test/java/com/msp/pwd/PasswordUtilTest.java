package com.msp.pwd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    @DisplayName("PasswordUtil facade hash and verify")
    void testFacadeHashAndVerify() {
        String raw = "UnifiedPasswordFacade99$";
        String hash = PasswordUtil.hash(raw);

        assertTrue(PasswordUtil.verify(raw, hash));
        assertFalse(PasswordUtil.verify("wrong", hash));
    }

    @Test
    @DisplayName("PasswordUtil facade strength validation")
    void testFacadeStrengthValidation() {
        assertTrue(PasswordUtil.isStrong("Str0ng!P@ssw0rd"));
        assertFalse(PasswordUtil.isStrong("weak"));

        PasswordStrengthResult result = PasswordUtil.validateStrength("Str0ng!P@ssw0rd");
        assertTrue(result.isValid());
        assertEquals(StrengthLevel.VERY_STRONG, result.level());
    }

    @Test
    @DisplayName("PasswordUtil secure random password generation")
    void testRandomPasswordGeneration() {
        for (int len : new int[]{8, 12, 16, 24}) {
            String pwd = PasswordUtil.generateRandomPassword(len, true);
            assertEquals(len, pwd.length());

            boolean hasUpper = pwd.chars().anyMatch(Character::isUpperCase);
            boolean hasLower = pwd.chars().anyMatch(Character::isLowerCase);
            boolean hasDigit = pwd.chars().anyMatch(Character::isDigit);

            assertTrue(hasUpper, "Random password should contain uppercase: " + pwd);
            assertTrue(hasLower, "Random password should contain lowercase: " + pwd);
            assertTrue(hasDigit, "Random password should contain digit: " + pwd);
        }
    }
}
