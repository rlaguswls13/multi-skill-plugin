package com.msp.pwdentropy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEntropyMeterTest {

    @Test
    @DisplayName("Should handle null and empty passwords gracefully")
    void testEmptyAndNullPasswords() {
        EntropyReport nullReport = PasswordEntropyMeter.evaluate(null);
        assertEquals(0.0, nullReport.entropyBits());
        assertEquals(PasswordStrength.VERY_WEAK, nullReport.strength());
        assertEquals(0, nullReport.poolSize());
        assertEquals(0, nullReport.length());
        assertFalse(nullReport.isAcceptable());
        assertFalse(nullReport.isStrong());

        EntropyReport emptyReport = PasswordEntropyMeter.evaluate("");
        assertEquals(0.0, emptyReport.entropyBits());
        assertEquals(PasswordStrength.VERY_WEAK, emptyReport.strength());
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "123456", "admin", "qwerty", "letmein", "PassWord123"})
    @DisplayName("Should flag common dictionary passwords as VERY_WEAK")
    void testCommonDictionaryPasswords(String common) {
        EntropyReport report = PasswordEntropyMeter.evaluate(common);
        assertEquals(PasswordStrength.VERY_WEAK, report.strength());
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("dictionary word") || w.contains("predictable")));
        assertFalse(report.isAcceptable());
    }

    @Test
    @DisplayName("Should detect repetitive character sequences and sequential runs")
    void testRepetitiveAndSequentialRuns() {
        assertTrue(PasswordEntropyMeter.hasRepetitiveSequences("aaaa"));
        assertTrue(PasswordEntropyMeter.hasRepetitiveSequences("test1111"));
        assertFalse(PasswordEntropyMeter.hasRepetitiveSequences("standard"));

        assertTrue(PasswordEntropyMeter.hasSequentialRuns("abcdef"));
        assertTrue(PasswordEntropyMeter.hasSequentialRuns("123456"));
        assertTrue(PasswordEntropyMeter.hasSequentialRuns("pass-qwerty-99"));
        assertTrue(PasswordEntropyMeter.hasSequentialRuns("cba"));
    }

    @Test
    @DisplayName("Should correctly classify character pools")
    void testPoolSizeCalculation() {
        assertEquals(10, PasswordEntropyMeter.calculatePoolSize("12345678"));
        assertEquals(26, PasswordEntropyMeter.calculatePoolSize("abcdefgh"));
        assertEquals(26, PasswordEntropyMeter.calculatePoolSize("ABCDEFGH"));
        assertEquals(52, PasswordEntropyMeter.calculatePoolSize("AbCdEfGh"));
        assertEquals(62, PasswordEntropyMeter.calculatePoolSize("AbCd1234"));
        assertEquals(94, PasswordEntropyMeter.calculatePoolSize("AbCd1234!@#$"));
    }

    @Test
    @DisplayName("Should evaluate Shannon entropy accurately")
    void testShannonEntropy() {
        // Uniform string "aaaa" has 0 Shannon entropy
        assertEquals(0.0, PasswordEntropyMeter.calculateShannonEntropy("aaaa"), 0.001);

        // String with 2 alternating characters has 1.0 bit per char
        assertEquals(1.0, PasswordEntropyMeter.calculateShannonEntropy("abab"), 0.001);
    }

    @Test
    @DisplayName("Should rate strong complex passwords as STRONG or VERY_STRONG")
    void testStrongPasswords() {
        EntropyReport report = PasswordEntropyMeter.evaluate("X9$mK#vL82@p!qZ5");
        assertTrue(report.entropyBits() >= 60.0, "Expected high entropy but was: " + report.entropyBits());
        assertTrue(report.strength() == PasswordStrength.STRONG || report.strength() == PasswordStrength.VERY_STRONG);
        assertTrue(report.isAcceptable());
        assertTrue(report.isStrong());
        assertEquals(16, report.length());
        assertEquals(94, report.poolSize());
    }

    @Test
    @DisplayName("Short passwords under 8 characters must be penalized")
    void testShortPasswords() {
        EntropyReport report = PasswordEntropyMeter.evaluate("A!1b");
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("too short")));
        assertFalse(report.isStrong());
    }
}
