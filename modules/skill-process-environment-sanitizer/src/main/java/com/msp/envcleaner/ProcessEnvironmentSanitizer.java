package com.msp.envcleaner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Child Process Environment Variable Sanitizer preventing API keys,
 * database passwords, tokens, and sensitive secrets from leaking to subprocesses.
 */
public final class ProcessEnvironmentSanitizer {

    public static final String DEFAULT_MASK = "***REDACTED***";

    /**
     * Standard regex pattern matching common secret and credential environment variable names.
     */
    public static final Pattern DEFAULT_SENSITIVE_PATTERN = Pattern.compile(
            "(?i).*(password|secret|key|token|auth|credential|cert|passphrase|private).*"
    );

    /**
     * Standard operating system and runtime variables considered safe by default.
     */
    public static final Set<String> DEFAULT_WHITELIST = Set.of(
            "PATH", "JAVA_HOME", "USER", "USERNAME", "HOME", "USERPROFILE",
            "LANG", "LC_ALL", "TMP", "TEMP", "SYSTEMROOT", "COMSPEC",
            "OS", "PATHEXT", "SHELL", "TERM", "PWD", "NUMBER_OF_PROCESSORS"
    );

    private final Set<String> caseInsensitiveWhitelist;
    private final List<Pattern> blacklistPatterns;
    private final SanitizeAction sensitiveAction;
    private final String maskReplacement;

    private ProcessEnvironmentSanitizer(Builder builder) {
        this.caseInsensitiveWhitelist = Set.copyOf(builder.whitelist);
        this.blacklistPatterns = List.copyOf(builder.blacklistPatterns);
        this.sensitiveAction = builder.sensitiveAction;
        this.maskReplacement = builder.maskReplacement;
    }

    /**
     * Returns a sanitizer configured with recommended safe defaults (scrubbing sensitive vars).
     */
    public static ProcessEnvironmentSanitizer defaultSanitizer() {
        return builder().build();
    }

    /**
     * Creates a new Builder for configuring sanitizer rules.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Sanitizes and applies clean environment variables directly to a {@link ProcessBuilder}.
     *
     * @param processBuilder the ProcessBuilder to sanitize
     * @return the same ProcessBuilder instance with sanitized environment
     */
    public ProcessBuilder applyTo(ProcessBuilder processBuilder) {
        Objects.requireNonNull(processBuilder, "processBuilder must not be null");
        Map<String, String> pbEnv = processBuilder.environment();
        Map<String, String> clean = sanitize(pbEnv);
        pbEnv.clear();
        pbEnv.putAll(clean);
        return processBuilder;
    }

    /**
     * Sanitizes the given environment variable map according to configured rules.
     *
     * @param env source environment map
     * @return new map containing only sanitized environment variables
     */
    public Map<String, String> sanitize(Map<String, String> env) {
        return audit(env).sanitizedEnvironment();
    }

    /**
     * Audits the given environment map, generating both the sanitized map and an audit log.
     *
     * @param env source environment map
     * @return SanitizationReport containing sanitized map and audit trail
     */
    public SanitizationReport audit(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return new SanitizationReport(Map.of(), List.of());
        }

        Map<String, String> resultEnv = new LinkedHashMap<>();
        List<SanitizationReport.AuditEntry> auditLog = new ArrayList<>();

        for (Map.Entry<String, String> entry : env.entrySet()) {
            String varName = entry.getKey();
            String varVal = entry.getValue();

            if (varName == null) {
                continue;
            }

            // 1. Whitelist check (overrides blacklist)
            if (caseInsensitiveWhitelist.contains(varName.toUpperCase())) {
                resultEnv.put(varName, varVal);
                auditLog.add(new SanitizationReport.AuditEntry(varName, SanitizeAction.PRESERVE, "Whitelisted safe variable"));
                continue;
            }

            // 2. Blacklist regex match check
            Pattern matchedPattern = findMatchingPattern(varName);
            if (matchedPattern != null) {
                switch (sensitiveAction) {
                    case REMOVE -> {
                        auditLog.add(new SanitizationReport.AuditEntry(
                                varName, SanitizeAction.REMOVE,
                                "Matched sensitive pattern: " + matchedPattern.pattern()));
                    }
                    case MASK -> {
                        resultEnv.put(varName, maskReplacement);
                        auditLog.add(new SanitizationReport.AuditEntry(
                                varName, SanitizeAction.MASK,
                                "Masked due to pattern: " + matchedPattern.pattern()));
                    }
                    case PRESERVE -> {
                        resultEnv.put(varName, varVal);
                        auditLog.add(new SanitizationReport.AuditEntry(
                                varName, SanitizeAction.PRESERVE,
                                "Preserved by configuration"));
                    }
                }
            } else {
                // 3. Non-sensitive variable
                resultEnv.put(varName, varVal);
                auditLog.add(new SanitizationReport.AuditEntry(varName, SanitizeAction.PRESERVE, "Non-sensitive variable"));
            }
        }

        return new SanitizationReport(resultEnv, auditLog);
    }

    private Pattern findMatchingPattern(String varName) {
        for (Pattern p : blacklistPatterns) {
            if (p.matcher(varName).matches()) {
                return p;
            }
        }
        return null;
    }

    /**
     * Builder for {@link ProcessEnvironmentSanitizer}.
     */
    public static final class Builder {
        private final Set<String> whitelist = new HashSet<>();
        private final List<Pattern> blacklistPatterns = new ArrayList<>();
        private SanitizeAction sensitiveAction = SanitizeAction.REMOVE;
        private String maskReplacement = DEFAULT_MASK;

        private Builder() {
            for (String w : DEFAULT_WHITELIST) {
                whitelist.add(w.toUpperCase());
            }
            blacklistPatterns.add(DEFAULT_SENSITIVE_PATTERN);
        }

        /**
         * Sets action to take when a variable matches sensitive pattern (REMOVE, MASK, or PRESERVE).
         */
        public Builder actionOnSensitive(SanitizeAction action) {
            this.sensitiveAction = Objects.requireNonNull(action, "action must not be null");
            return this;
        }

        /**
         * Sets replacement string used when {@link SanitizeAction#MASK} is selected.
         */
        public Builder maskReplacement(String mask) {
            this.maskReplacement = Objects.requireNonNull(mask, "maskReplacement must not be null");
            return this;
        }

        /**
         * Adds environment variable names to the whitelist.
         */
        public Builder addWhitelist(String... vars) {
            if (vars != null) {
                for (String v : vars) {
                    if (v != null && !v.isBlank()) {
                        whitelist.add(v.trim().toUpperCase());
                    }
                }
            }
            return this;
        }

        /**
         * Adds environment variable names to the whitelist from a collection.
         */
        public Builder addWhitelist(Collection<String> vars) {
            if (vars != null) {
                for (String v : vars) {
                    if (v != null && !v.isBlank()) {
                        whitelist.add(v.trim().toUpperCase());
                    }
                }
            }
            return this;
        }

        /**
         * Clears default whitelist.
         */
        public Builder clearWhitelist() {
            whitelist.clear();
            return this;
        }

        /**
         * Adds a custom sensitive regex pattern.
         */
        public Builder addBlacklistPattern(Pattern pattern) {
            Objects.requireNonNull(pattern, "pattern must not be null");
            blacklistPatterns.add(pattern);
            return this;
        }

        /**
         * Adds a custom sensitive regex string.
         */
        public Builder addBlacklistPattern(String regex) {
            Objects.requireNonNull(regex, "regex must not be null");
            blacklistPatterns.add(Pattern.compile(regex));
            return this;
        }

        /**
         * Clears default sensitive patterns.
         */
        public Builder clearBlacklist() {
            blacklistPatterns.clear();
            return this;
        }

        public ProcessEnvironmentSanitizer build() {
            return new ProcessEnvironmentSanitizer(this);
        }
    }
}
