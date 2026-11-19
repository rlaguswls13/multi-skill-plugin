package com.msp.envcleaner;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detailed audit report of an environment sanitization operation.
 *
 * @param sanitizedEnvironment the resulting sanitized environment variable map
 * @param auditLog             ordered audit log entries explaining actions taken for each variable
 */
public record SanitizationReport(
        Map<String, String> sanitizedEnvironment,
        List<AuditEntry> auditLog
) {
    public SanitizationReport {
        Objects.requireNonNull(sanitizedEnvironment, "sanitizedEnvironment must not be null");
        Objects.requireNonNull(auditLog, "auditLog must not be null");
        sanitizedEnvironment = Collections.unmodifiableMap(Map.copyOf(sanitizedEnvironment));
        auditLog = Collections.unmodifiableList(List.copyOf(auditLog));
    }

    /**
     * Audit entry for an individual environment variable.
     */
    public record AuditEntry(String variableName, SanitizeAction action, String reason) {
        public AuditEntry {
            Objects.requireNonNull(variableName, "variableName must not be null");
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /**
     * Number of variables completely removed.
     */
    public long scrubbedCount() {
        return auditLog.stream().filter(e -> e.action() == SanitizeAction.REMOVE).count();
    }

    /**
     * Number of variables masked.
     */
    public long maskedCount() {
        return auditLog.stream().filter(e -> e.action() == SanitizeAction.MASK).count();
    }

    /**
     * Number of variables preserved.
     */
    public long preservedCount() {
        return auditLog.stream().filter(e -> e.action() == SanitizeAction.PRESERVE).count();
    }
}
