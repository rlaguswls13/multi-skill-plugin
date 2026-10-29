package com.msp.diff;

import java.util.*;

/**
 * Formats diff results into human-readable audit logs or RFC 6902 JSON patch operations,
 * and applies forward patches or backward rollbacks to Map/List structures.
 */
public final class AuditPatcher {

    private AuditPatcher() {
    }

    /**
     * Formats the diff result into human-readable audit logs.
     *
     * @param diff DiffResult
     * @return list of audit log messages
     */
    public static List<String> formatAuditLogs(DiffResult diff) {
        if (diff == null) {
            return Collections.emptyList();
        }
        return diff.toAuditLogs();
    }

    /**
     * Converts a DiffResult into RFC 6902 compliant JSON patch operation maps.
     *
     * @param diff DiffResult
     * @return list of patch operation maps
     */
    public static List<Map<String, Object>> toJsonPatch(DiffResult diff) {
        if (diff == null || !diff.hasChanges()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> ops = new ArrayList<>();
        for (FieldChange change : diff.changes()) {
            String rfcPath = toRfc6902Path(change.path());
            Map<String, Object> op = new LinkedHashMap<>();
            switch (change.changeType()) {
                case ADDED -> {
                    op.put("op", "add");
                    op.put("path", rfcPath);
                    op.put("value", change.newValue());
                }
                case REMOVED -> {
                    op.put("op", "remove");
                    op.put("path", rfcPath);
                }
                case MODIFIED -> {
                    op.put("op", "replace");
                    op.put("path", rfcPath);
                    op.put("value", change.newValue());
                }
            }
            ops.add(op);
        }
        return ops;
    }

    /**
     * Applies a DiffResult forward to the target map.
     *
     * @param target map to update in-place
     * @param diff   DiffResult to apply
     */
    public static void apply(Map<String, Object> target, DiffResult diff) {
        if (target == null || diff == null || !diff.hasChanges()) {
            return;
        }

        for (FieldChange change : diff.changes()) {
            switch (change.changeType()) {
                case ADDED, MODIFIED -> setValue(target, change.path(), change.newValue());
                case REMOVED -> removeValue(target, change.path());
            }
        }
    }

    /**
     * Rolls back a DiffResult on the target map, restoring original values.
     *
     * @param target map to rollback in-place
     * @param diff   DiffResult containing changes to revert
     */
    public static void rollback(Map<String, Object> target, DiffResult diff) {
        if (target == null || diff == null || !diff.hasChanges()) {
            return;
        }

        // Apply rollbacks in reverse order
        List<FieldChange> reversed = new ArrayList<>(diff.changes());
        Collections.reverse(reversed);

        for (FieldChange change : reversed) {
            switch (change.changeType()) {
                case ADDED -> removeValue(target, change.path());
                case REMOVED, MODIFIED -> setValue(target, change.path(), change.oldValue());
            }
        }
    }

    /**
     * Converts dot/bracket path (e.g. "address.city", "items[0].price") to RFC 6902 path ("/address/city", "/items/0/price").
     */
    public static String toRfc6902Path(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path.replace('[', '/').replace(']', '.');
        String[] parts = normalized.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!part.startsWith("/")) {
                    sb.append("/");
                }
                sb.append(part);
            }
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    // --- Path Navigation and Mutation ---

    private sealed interface PathSegment permits PropSeg, IdxSeg {}
    private record PropSeg(String name) implements PathSegment {}
    private record IdxSeg(int index) implements PathSegment {}

    private static List<PathSegment> parseSegments(String path) {
        List<PathSegment> segments = new ArrayList<>();
        int len = path.length();
        int i = 0;
        StringBuilder buf = new StringBuilder();

        while (i < len) {
            char c = path.charAt(i);
            if (c == '.') {
                if (!buf.isEmpty()) {
                    segments.add(new PropSeg(buf.toString()));
                    buf.setLength(0);
                }
                i++;
            } else if (c == '[') {
                if (!buf.isEmpty()) {
                    segments.add(new PropSeg(buf.toString()));
                    buf.setLength(0);
                }
                i++;
                StringBuilder num = new StringBuilder();
                while (i < len && path.charAt(i) != ']') {
                    num.append(path.charAt(i++));
                }
                if (i < len && path.charAt(i) == ']') {
                    i++;
                }
                segments.add(new IdxSeg(Integer.parseInt(num.toString().trim())));
            } else {
                buf.append(c);
                i++;
            }
        }
        if (!buf.isEmpty()) {
            segments.add(new PropSeg(buf.toString()));
        }
        return segments;
    }

    @SuppressWarnings("unchecked")
    private static void setValue(Map<String, Object> root, String path, Object value) {
        List<PathSegment> segments = parseSegments(path);
        if (segments.isEmpty()) {
            return;
        }

        Object current = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            PathSegment seg = segments.get(i);
            PathSegment nextSeg = segments.get(i + 1);

            if (seg instanceof PropSeg p) {
                Map<String, Object> map = (Map<String, Object>) current;
                Object next = map.get(p.name());
                if (next == null) {
                    next = (nextSeg instanceof IdxSeg) ? new ArrayList<>() : new LinkedHashMap<String, Object>();
                    map.put(p.name(), next);
                }
                current = next;
            } else if (seg instanceof IdxSeg idx) {
                List<Object> list = (List<Object>) current;
                while (list.size() <= idx.index()) {
                    list.add(null);
                }
                Object next = list.get(idx.index());
                if (next == null) {
                    next = (nextSeg instanceof IdxSeg) ? new ArrayList<>() : new LinkedHashMap<String, Object>();
                    list.set(idx.index(), next);
                }
                current = next;
            }
        }

        PathSegment last = segments.get(segments.size() - 1);
        if (last instanceof PropSeg p) {
            ((Map<String, Object>) current).put(p.name(), value);
        } else if (last instanceof IdxSeg idx) {
            List<Object> list = (List<Object>) current;
            while (list.size() < idx.index()) {
                list.add(null);
            }
            if (idx.index() < list.size()) {
                list.set(idx.index(), value);
            } else {
                list.add(value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeValue(Map<String, Object> root, String path) {
        List<PathSegment> segments = parseSegments(path);
        if (segments.isEmpty()) {
            return;
        }

        Object current = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            PathSegment seg = segments.get(i);
            if (current == null) {
                return;
            }
            if (seg instanceof PropSeg p && current instanceof Map<?, ?> map) {
                current = map.get(p.name());
            } else if (seg instanceof IdxSeg idx && current instanceof List<?> list) {
                if (idx.index() < list.size()) {
                    current = list.get(idx.index());
                } else {
                    return;
                }
            } else {
                return;
            }
        }

        if (current == null) {
            return;
        }

        PathSegment last = segments.get(segments.size() - 1);
        if (last instanceof PropSeg p && current instanceof Map<?, ?> map) {
            map.remove(p.name());
        } else if (last instanceof IdxSeg idx && current instanceof List<?> list) {
            if (idx.index() < list.size()) {
                list.remove(idx.index());
            }
        }
    }
}
