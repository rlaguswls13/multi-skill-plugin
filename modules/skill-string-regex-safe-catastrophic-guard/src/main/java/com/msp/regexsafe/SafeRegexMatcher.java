package com.msp.regexsafe;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Main entry point for ReDoS-safe regular expression matching and vulnerability inspection.
 */
public final class SafeRegexMatcher {

    private SafeRegexMatcher() {}

    public static SafePattern compile(String regex) {
        return compile(regex, SafeRegexOptions.defaults());
    }

    public static SafePattern compile(String regex, SafeRegexOptions options) {
        return compile(regex, 0, options);
    }

    public static SafePattern compile(String regex, int flags, SafeRegexOptions options) {
        Objects.requireNonNull(regex, "regex cannot be null");
        Objects.requireNonNull(options, "options cannot be null");

        if (options.enableStaticPreCheck()) {
            RedosRiskAnalysis analysis = RedosVulnerabilityDetector.analyze(regex);
            if (analysis.riskLevel() == RedosRiskLevel.CRITICAL && options.throwOnFailure()) {
                throw new IllegalArgumentException("Rejected high-risk ReDoS regex: " + analysis.warnings());
            }
        }

        Pattern pattern = Pattern.compile(regex, flags);
        return new SafePattern(pattern, options);
    }

    public static RegexMatchResult matches(String regex, CharSequence input) {
        return matches(regex, input, SafeRegexOptions.defaults());
    }

    public static RegexMatchResult matches(String regex, CharSequence input, SafeRegexOptions options) {
        return compile(regex, options).matches(input);
    }

    public static RegexMatchResult find(String regex, CharSequence input) {
        return find(regex, input, SafeRegexOptions.defaults());
    }

    public static RegexMatchResult find(String regex, CharSequence input, SafeRegexOptions options) {
        return compile(regex, options).find(input);
    }

    public static RedosRiskAnalysis analyzeRisk(String regex) {
        return RedosVulnerabilityDetector.analyze(regex);
    }
}
