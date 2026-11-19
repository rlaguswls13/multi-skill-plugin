package com.msp.regexsafe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SafeRegexMatcherTest {

    @Test
    @DisplayName("Normal safe regex matching produces correct match and groups")
    void testNormalMatching() {
        SafePattern pattern = SafeRegexMatcher.compile("(\\w+)@([a-z0-9.-]+)");
        RegexMatchResult result = pattern.find("Contact user@example.com for info");

        assertTrue(result.isMatched());
        assertFalse(result.isTimedOut());
        assertFalse(result.isStepLimitExceeded());
        assertEquals("user@example.com", result.matchedText());
        assertEquals(3, result.groups().size());
        assertEquals("user", result.groups().get(1));
        assertEquals("example.com", result.groups().get(2));
        assertTrue(result.stepCount() > 0);
    }

    @Test
    @DisplayName("Catastrophic backtracking is intercepted and terminated by step limit")
    void testCatastrophicBacktrackingIntercepted() {
        // Evil ReDoS nested quantifier pattern: ((a+)+)+b
        String evilRegex = "((a+)+)+b";
        String attackInput = "aaaaaaaaaaaaaaaaaaaa!";

        SafeRegexOptions options = SafeRegexOptions.defaults()
                .withMaxSteps(500)
                .withTimeout(Duration.ofSeconds(1));

        RegexMatchResult result = SafeRegexMatcher.matches(evilRegex, attackInput, options);

        assertFalse(result.isMatched());
        assertTrue(result.isStepLimitExceeded());
        assertEquals(501, result.stepCount());
    }

    @Test
    @DisplayName("Regex execution deadline terminates long-running backtracking")
    void testTimeoutTermination() {
        String evilRegex = "((a+)+)+b";
        String attackInput = "aaaaaaaaaaaaaaaaaaaaaaaa!";

        SafeRegexOptions options = SafeRegexOptions.defaults()
                .withMaxSteps(10_000_000)
                .withTimeout(Duration.ofMillis(30));

        RegexMatchResult result = SafeRegexMatcher.matches(evilRegex, attackInput, options);

        assertFalse(result.isMatched());
        assertTrue(result.isTimedOut());
    }

    @Test
    @DisplayName("Static vulnerability detector identifies dangerous ReDoS patterns")
    void testStaticVulnerabilityDetector() {
        RedosRiskAnalysis critical = RedosVulnerabilityDetector.analyze("^(.*)+$");
        assertEquals(RedosRiskLevel.CRITICAL, critical.riskLevel());
        assertTrue(critical.isVulnerable());
        assertFalse(critical.warnings().isEmpty());

        RedosRiskAnalysis high = RedosVulnerabilityDetector.analyze("([a-z]+)+");
        assertEquals(RedosRiskLevel.HIGH, high.riskLevel());
        assertTrue(high.isVulnerable());

        RedosRiskAnalysis medium = RedosVulnerabilityDetector.analyze("(cat|catch)+");
        assertEquals(RedosRiskLevel.MEDIUM, medium.riskLevel());

        RedosRiskAnalysis safe = RedosVulnerabilityDetector.analyze("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");
        assertEquals(RedosRiskLevel.SAFE, safe.riskLevel());
        assertFalse(safe.isVulnerable());
    }

    @Test
    @DisplayName("Throw on failure mode raises exception directly")
    void testThrowOnFailure() {
        SafeRegexOptions options = SafeRegexOptions.defaults()
                .withMaxSteps(500)
                .withThrowOnFailure(true);

        assertThrows(CatastrophicBacktrackingException.class, () ->
                SafeRegexMatcher.matches("((a+)+)+b", "aaaaaaaaaaaaaaaaaaaa!", options)
        );
    }
}
