package com.msp.diff;

import java.util.List;
import java.util.Map;

/**
 * Unified facade for detecting differences, generating audit logs,
 * producing RFC 6902 JSON patches, and applying/rolling back changes.
 */
public final class DiffPatcher {

    private DiffPatcher() {
    }

    /**
     * Recursively computes differences between two objects.
     */
    public static DiffResult diff(Object oldObj, Object newObj) {
        return DiffDetector.compare(oldObj, newObj);
    }

    /**
     * Formats changes into readable audit log strings.
     */
    public static List<String> formatAuditLogs(DiffResult diff) {
        return AuditPatcher.formatAuditLogs(diff);
    }

    /**
     * Converts a diff into standard RFC 6902 JSON patch operation maps.
     */
    public static List<Map<String, Object>> toJsonPatch(DiffResult diff) {
        return AuditPatcher.toJsonPatch(diff);
    }

    /**
     * Applies changes in-place to the target map.
     */
    public static void apply(Map<String, Object> target, DiffResult diff) {
        AuditPatcher.apply(target, diff);
    }

    /**
     * Rolls back changes in-place on the target map.
     */
    public static void rollback(Map<String, Object> target, DiffResult diff) {
        AuditPatcher.rollback(target, diff);
    }
}
