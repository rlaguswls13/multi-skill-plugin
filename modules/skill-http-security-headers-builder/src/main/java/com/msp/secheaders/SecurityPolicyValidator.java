package com.msp.secheaders;

import java.util.*;

/**
 * Validates security header configurations for dangerous antipatterns and missing protections.
 */
public final class SecurityPolicyValidator {

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public record SecurityIssue(Severity severity, String headerName, String message) {
    }

    public record ValidationReport(List<SecurityIssue> issues) {
        public boolean isValid() {
            return issues.stream().noneMatch(i -> i.severity() == Severity.ERROR);
        }

        public boolean hasWarnings() {
            return issues.stream().anyMatch(i -> i.severity() == Severity.WARNING);
        }

        public List<SecurityIssue> issuesBySeverity(Severity severity) {
            return issues.stream().filter(i -> i.severity() == severity).toList();
        }
    }

    public static ValidationReport validate(SecurityHeaders headers) {
        Objects.requireNonNull(headers, "headers cannot be null");
        List<SecurityIssue> issues = new ArrayList<>();

        validateHsts(headers, issues);
        validateCsp(headers, issues);
        validateFraming(headers, issues);
        validateContentTypeOptions(headers, issues);
        validateReferrerPolicy(headers, issues);
        validateCrossoriginIsolation(headers, issues);

        return new ValidationReport(Collections.unmodifiableList(issues));
    }

    private static void validateHsts(SecurityHeaders headers, List<SecurityIssue> issues) {
        HstsConfig hsts = headers.hstsConfig();
        if (hsts == null) {
            issues.add(new SecurityIssue(
                    Severity.WARNING,
                    SecurityHeaders.HEADER_HSTS,
                    "Strict-Transport-Security (HSTS) header is missing; HTTPS cannot be strictly enforced."
            ));
            return;
        }

        if (hsts.preload()) {
            if (hsts.maxAgeSeconds() < HstsConfig.DEFAULT_MAX_AGE_SECONDS) {
                issues.add(new SecurityIssue(
                        Severity.ERROR,
                        SecurityHeaders.HEADER_HSTS,
                        "HSTS preload requires max-age to be at least 31,536,000 seconds (1 year)."
                ));
            }
            if (!hsts.includeSubDomains()) {
                issues.add(new SecurityIssue(
                        Severity.ERROR,
                        SecurityHeaders.HEADER_HSTS,
                        "HSTS preload requires 'includeSubDomains' to be true."
                ));
            }
        }
    }

    private static void validateCsp(SecurityHeaders headers, List<SecurityIssue> issues) {
        CspConfig csp = headers.cspConfig();
        if (csp == null) {
            issues.add(new SecurityIssue(
                    Severity.WARNING,
                    SecurityHeaders.HEADER_CSP,
                    "Content-Security-Policy (CSP) is missing; application is exposed to XSS and data injection."
            ));
            return;
        }

        Map<String, List<String>> directives = csp.directives();

        // Check script-src or default-src
        List<String> scriptSrc = directives.get("script-src");
        List<String> defaultSrc = directives.get("default-src");

        List<String> effectiveScript = scriptSrc != null ? scriptSrc : defaultSrc;
        if (effectiveScript == null) {
            issues.add(new SecurityIssue(
                    Severity.WARNING,
                    SecurityHeaders.HEADER_CSP,
                    "Neither 'script-src' nor 'default-src' directive is defined in CSP."
            ));
        } else {
            boolean hasUnsafeInline = effectiveScript.stream().anyMatch(s -> s.equalsIgnoreCase("'unsafe-inline'"));
            boolean hasNonceOrHash = effectiveScript.stream().anyMatch(s ->
                    s.startsWith("'nonce-") || s.startsWith("'sha256-") || s.startsWith("'sha384-") || s.startsWith("'sha512-"));

            if (hasUnsafeInline && !hasNonceOrHash) {
                issues.add(new SecurityIssue(
                        Severity.WARNING,
                        SecurityHeaders.HEADER_CSP,
                        "'script-src' contains ''unsafe-inline'' without nonce or hash, defeating XSS mitigation."
                ));
            }

            if (effectiveScript.stream().anyMatch(s -> s.equalsIgnoreCase("'unsafe-eval'"))) {
                issues.add(new SecurityIssue(
                        Severity.WARNING,
                        SecurityHeaders.HEADER_CSP,
                        "'script-src' contains ''unsafe-eval'', allowing arbitrary string-to-code execution."
                ));
            }

            if (effectiveScript.contains("*")) {
                issues.add(new SecurityIssue(
                        Severity.ERROR,
                        SecurityHeaders.HEADER_CSP,
                        "Wildcard '*' in script sources allows arbitrary script execution from any domain."
                ));
            }
        }

        // Check object-src
        List<String> objectSrc = directives.get("object-src");
        if (objectSrc == null && defaultSrc == null) {
            issues.add(new SecurityIssue(
                    Severity.INFO,
                    SecurityHeaders.HEADER_CSP,
                    "No 'object-src' or 'default-src' directive specified. Recommend 'object-src ''none'' to block Flash/Java applets."
            ));
        } else if (objectSrc != null && !objectSrc.contains("'none'")) {
            issues.add(new SecurityIssue(
                    Severity.INFO,
                    SecurityHeaders.HEADER_CSP,
                    "'object-src' is not set to ''none''. Recommend blocking plugin execution."
            ));
        }
    }

    private static void validateFraming(SecurityHeaders headers, List<SecurityIssue> issues) {
        boolean hasXfo = headers.frameOptions() != null;
        boolean hasCspFrameAncestors = headers.cspConfig() != null &&
                headers.cspConfig().directives().containsKey("frame-ancestors");

        if (!hasXfo && !hasCspFrameAncestors) {
            issues.add(new SecurityIssue(
                    Severity.WARNING,
                    SecurityHeaders.HEADER_X_FRAME_OPTIONS,
                    "Neither X-Frame-Options nor CSP frame-ancestors is configured; vulnerable to clickjacking."
            ));
        }
    }

    private static void validateContentTypeOptions(SecurityHeaders headers, List<SecurityIssue> issues) {
        Optional<String> nosniff = headers.getHeader(SecurityHeaders.HEADER_X_CONTENT_TYPE_OPTIONS);
        if (nosniff.isEmpty() || !nosniff.get().equalsIgnoreCase("nosniff")) {
            issues.add(new SecurityIssue(
                    Severity.WARNING,
                    SecurityHeaders.HEADER_X_CONTENT_TYPE_OPTIONS,
                    "X-Content-Type-Options: nosniff is missing or not set to 'nosniff'; browser may execute MIME-sniffed scripts."
            ));
        }
    }

    private static void validateReferrerPolicy(SecurityHeaders headers, List<SecurityIssue> issues) {
        ReferrerPolicy rp = headers.referrerPolicy();
        if (rp == ReferrerPolicy.UNSAFE_URL) {
            issues.add(new SecurityIssue(
                    Severity.WARNING,
                    SecurityHeaders.HEADER_REFERRER_POLICY,
                    "Referrer-Policy is set to 'unsafe-url'; sensitive path, query tokens, or credentials may leak across origins."
            ));
        }
    }

    private static void validateCrossoriginIsolation(SecurityHeaders headers, List<SecurityIssue> issues) {
        CoepPolicy coep = headers.coepPolicy();
        CoopPolicy coop = headers.coopPolicy();

        if (coep == CoepPolicy.REQUIRE_CORP && (coop == null || coop == CoopPolicy.UNSAFE_NONE)) {
            issues.add(new SecurityIssue(
                    Severity.WARNING,
                    SecurityHeaders.HEADER_COEP,
                    "COEP 'require-corp' is enabled without COOP 'same-origin'. Cross-origin isolation will not be fully active."
            ));
        }
    }
}
