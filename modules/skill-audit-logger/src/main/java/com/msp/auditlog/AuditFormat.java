package com.msp.auditlog;

import java.time.Instant;
import java.util.*;

/**
 * Audit event formatting strategies: JSON string serializer and Key-Value serializer.
 */
public enum AuditFormat {
    JSON {
        @Override
        public String format(AuditEvent event, DataMasker masker) {
            StringBuilder sb = new StringBuilder(256);
            sb.append('{');
            appendJsonKeyValue(sb, "id", event.id(), true);
            appendJsonKeyValue(sb, "timestamp", event.timestamp().toString(), false);
            appendJsonKeyValue(sb, "actor", event.actor(), false);
            appendJsonKeyValue(sb, "action", event.action(), false);
            if (event.resource() != null) {
                appendJsonKeyValue(sb, "resource", event.resource(), false);
            }
            if (event.clientIp() != null) {
                appendJsonKeyValue(sb, "clientIp", event.clientIp(), false);
            }
            appendJsonKeyValue(sb, "status", event.status(), false);

            // Details
            sb.append(",\"details\":{");
            int detailCount = 0;
            for (Map.Entry<String, Object> entry : event.details().entrySet()) {
                if (detailCount++ > 0) sb.append(',');
                writeJsonString(sb, entry.getKey());
                sb.append(':');
                Object maskedVal = masker != null ? masker.mask(entry.getKey(), entry.getValue()) : entry.getValue();
                writeJsonValue(sb, maskedVal, masker);
            }
            sb.append('}');

            // Diffs
            sb.append(",\"diffs\":[");
            for (int i = 0; i < event.diffs().size(); i++) {
                if (i > 0) sb.append(',');
                FieldDiff diff = event.diffs().get(i);
                sb.append('{');
                appendJsonKeyValue(sb, "field", diff.field(), true);
                sb.append(",\"oldValue\":");
                Object maskedOld = masker != null ? masker.mask(diff.field(), diff.oldValue()) : diff.oldValue();
                writeJsonValue(sb, maskedOld, masker);
                sb.append(",\"newValue\":");
                Object maskedNew = masker != null ? masker.mask(diff.field(), diff.newValue()) : diff.newValue();
                writeJsonValue(sb, maskedNew, masker);
                sb.append('}');
            }
            sb.append(']');

            sb.append('}');
            return sb.toString();
        }
    },

    KEY_VALUE {
        @Override
        public String format(AuditEvent event, DataMasker masker) {
            StringBuilder sb = new StringBuilder(256);
            sb.append("id=\"").append(escapeSimple(event.id())).append("\"");
            sb.append(" timestamp=\"").append(event.timestamp()).append("\"");
            sb.append(" actor=\"").append(escapeSimple(event.actor())).append("\"");
            sb.append(" action=\"").append(escapeSimple(event.action())).append("\"");
            if (event.resource() != null) {
                sb.append(" resource=\"").append(escapeSimple(event.resource())).append("\"");
            }
            if (event.clientIp() != null) {
                sb.append(" clientIp=\"").append(escapeSimple(event.clientIp())).append("\"");
            }
            sb.append(" status=\"").append(escapeSimple(event.status())).append("\"");

            // Details
            sb.append(" details={");
            int idx = 0;
            for (Map.Entry<String, Object> entry : event.details().entrySet()) {
                if (idx++ > 0) sb.append(", ");
                Object masked = masker != null ? masker.mask(entry.getKey(), entry.getValue()) : entry.getValue();
                sb.append(entry.getKey()).append("=").append(formatKvValue(masked));
            }
            sb.append("}");

            // Diffs
            if (!event.diffs().isEmpty()) {
                sb.append(" diffs=[");
                for (int i = 0; i < event.diffs().size(); i++) {
                    if (i > 0) sb.append(", ");
                    FieldDiff diff = event.diffs().get(i);
                    Object oldVal = masker != null ? masker.mask(diff.field(), diff.oldValue()) : diff.oldValue();
                    Object newVal = masker != null ? masker.mask(diff.field(), diff.newValue()) : diff.newValue();
                    sb.append(diff.field()).append(":(")
                            .append(formatKvValue(oldVal))
                            .append(" -> ")
                            .append(formatKvValue(newVal))
                            .append(")");
                }
                sb.append("]");
            }

            return sb.toString();
        }
    };

    public abstract String format(AuditEvent event, DataMasker masker);

    private static void appendJsonKeyValue(StringBuilder sb, String key, String value, boolean isFirst) {
        if (!isFirst) sb.append(',');
        writeJsonString(sb, key);
        sb.append(':');
        if (value == null) {
            sb.append("null");
        } else {
            writeJsonString(sb, value);
        }
    }

    private static void writeJsonValue(StringBuilder sb, Object val, DataMasker masker) {
        if (val == null) {
            sb.append("null");
        } else if (val instanceof Boolean || val instanceof Number) {
            sb.append(val);
        } else if (val instanceof String s) {
            writeJsonString(sb, s);
        } else if (val instanceof Instant instant) {
            writeJsonString(sb, instant.toString());
        } else if (val instanceof Map<?, ?> map) {
            sb.append('{');
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ > 0) sb.append(',');
                writeJsonString(sb, entry.getKey() != null ? entry.getKey().toString() : "");
                sb.append(':');
                String subKey = entry.getKey() != null ? entry.getKey().toString() : "";
                Object subVal = masker != null ? masker.mask(subKey, entry.getValue()) : entry.getValue();
                writeJsonValue(sb, subVal, masker);
            }
            sb.append('}');
        } else if (val instanceof Collection<?> coll) {
            sb.append('[');
            int count = 0;
            for (Object item : coll) {
                if (count++ > 0) sb.append(',');
                Object subVal = masker != null ? masker.mask("", item) : item;
                writeJsonValue(sb, subVal, masker);
            }
            sb.append(']');
        } else {
            writeJsonString(sb, val.toString());
        }
    }

    private static void writeJsonString(StringBuilder sb, String str) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static String formatKvValue(Object val) {
        if (val == null) return "null";
        if (val instanceof String s) return "\"" + escapeSimple(s) + "\"";
        return val.toString();
    }

    private static String escapeSimple(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}
