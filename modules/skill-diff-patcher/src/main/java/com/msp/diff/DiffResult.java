package com.msp.diff;

import java.util.Collections;
import java.util.List;

/**
 * Immutable container containing all detected field changes between two structures.
 */
public record DiffResult(List<FieldChange> changes) {

    public DiffResult {
        changes = (changes == null) ? Collections.emptyList() : List.copyOf(changes);
    }

    public static DiffResult empty() {
        return new DiffResult(Collections.emptyList());
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    public int changeCount() {
        return changes.size();
    }

    public List<FieldChange> getChangesByType(ChangeType type) {
        return changes.stream()
                .filter(c -> c.changeType() == type)
                .toList();
    }

    /**
     * Converts all changes into human-readable audit log lines.
     */
    public List<String> toAuditLogs() {
        return changes.stream()
                .map(FieldChange::toAuditString)
                .toList();
    }

    /**
     * Formats all changes into a single multiline string.
     */
    public String toAuditString() {
        return String.join(System.lineSeparator(), toAuditLogs());
    }
}
