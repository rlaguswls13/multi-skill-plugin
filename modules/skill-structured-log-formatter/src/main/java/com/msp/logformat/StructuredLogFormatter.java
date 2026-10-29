package com.msp.logformat;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance, zero-dependency structured JSON log formatter.
 * Formats log events adhering to common log collector standards (Elasticsearch/Logstash/Fluentd).
 */
public class StructuredLogFormatter {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final int DEFAULT_STACK_TRACE_LIMIT = 30;

    private final boolean pretty;
    private final int maxStackTraceDepth;
    private final boolean includeEmptyContext;

    public StructuredLogFormatter() {
        this(false, DEFAULT_STACK_TRACE_LIMIT, false);
    }

    public StructuredLogFormatter(boolean pretty, int maxStackTraceDepth, boolean includeEmptyContext) {
        this.pretty = pretty;
        this.maxStackTraceDepth = maxStackTraceDepth;
        this.includeEmptyContext = includeEmptyContext;
    }

    public static StructuredLogFormatter compact() {
        return new StructuredLogFormatter(false, DEFAULT_STACK_TRACE_LIMIT, false);
    }

    public static StructuredLogFormatter pretty() {
        return new StructuredLogFormatter(true, DEFAULT_STACK_TRACE_LIMIT, true);
    }

    public String format(LogEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        StringBuilder sb = new StringBuilder(256);
        String nl = pretty ? "\n" : "";
        String indent = pretty ? "  " : "";

        sb.append("{").append(nl);

        // 1. @timestamp
        sb.append(indent).append("\"@timestamp\": \"")
                .append(ISO_FORMATTER.format(event.timestamp())).append("\",").append(nl);

        // 2. level
        sb.append(indent).append("\"level\": \"")
                .append(escapeJson(event.level())).append("\",").append(nl);

        // 3. logger
        sb.append(indent).append("\"logger\": \"")
                .append(escapeJson(event.logger())).append("\",").append(nl);

        // 4. thread
        sb.append(indent).append("\"thread\": \"")
                .append(escapeJson(event.thread())).append("\",").append(nl);

        // 5. message
        sb.append(indent).append("\"message\": \"")
                .append(escapeJson(event.message())).append("\"");

        // 6. context map
        Map<String, ?> context = event.context();
        if (context != null && (!context.isEmpty() || includeEmptyContext)) {
            sb.append(",").append(nl);
            sb.append(indent).append("\"context\": ");
            writeJsonValue(sb, context, pretty ? 1 : 0);
        }

        // 7. error / exception
        if (event.error() != null) {
            sb.append(",").append(nl);
            sb.append(indent).append("\"error\": ");
            writeError(sb, event.error(), pretty ? 1 : 0);
        }

        sb.append(nl);
        sb.append("}");
        return sb.toString();
    }

    public String format(String level, String logger, String message) {
        return format(LogEvent.builder()
                .level(level)
                .logger(logger)
                .message(message)
                .build());
    }

    public String format(String level, String logger, String message, Map<String, ?> context) {
        return format(LogEvent.builder()
                .level(level)
                .logger(logger)
                .message(message)
                .context(context)
                .build());
    }

    public String format(String level, String logger, String message, Throwable error) {
        return format(LogEvent.builder()
                .level(level)
                .logger(logger)
                .message(message)
                .error(error)
                .build());
    }

    public String format(String level, String logger, String message, Map<String, ?> context, Throwable error) {
        return format(LogEvent.builder()
                .level(level)
                .logger(logger)
                .message(message)
                .context(context)
                .error(error)
                .build());
    }

    private void writeError(StringBuilder sb, Throwable t, int depth) {
        String nl = pretty ? "\n" : "";
        String ind = pretty ? "  ".repeat(depth) : "";
        String childInd = pretty ? "  ".repeat(depth + 1) : "";
        String arrInd = pretty ? "  ".repeat(depth + 2) : "";

        sb.append("{").append(nl);
        sb.append(childInd).append("\"type\": \"").append(escapeJson(t.getClass().getName())).append("\",").append(nl);
        sb.append(childInd).append("\"message\": \"").append(escapeJson(t.getMessage() != null ? t.getMessage() : "")).append("\",").append(nl);

        // StackTrace
        sb.append(childInd).append("\"stackTrace\": [").append(nl);
        StackTraceElement[] traces = t.getStackTrace();
        int limit = maxStackTraceDepth > 0 ? Math.min(traces.length, maxStackTraceDepth) : traces.length;
        for (int i = 0; i < limit; i++) {
            sb.append(arrInd).append("\"").append(escapeJson(traces[i].toString())).append("\"");
            if (i < limit - 1 || limit < traces.length) {
                sb.append(",");
            }
            sb.append(nl);
        }
        if (limit < traces.length) {
            sb.append(arrInd).append("\"... ").append(traces.length - limit).append(" more truncated frames\"").append(nl);
        }
        sb.append(childInd).append("]");

        if (t.getCause() != null && t.getCause() != t) {
            sb.append(",").append(nl);
            sb.append(childInd).append("\"causedBy\": ");
            writeError(sb, t.getCause(), depth + 1);
        }

        sb.append(nl);
        sb.append(ind).append("}");
    }

    private void writeJsonValue(StringBuilder sb, Object val, int depth) {
        if (val == null) {
            sb.append("null");
        } else if (val instanceof Boolean || val instanceof Number) {
            sb.append(val);
        } else if (val instanceof String s) {
            sb.append("\"").append(escapeJson(s)).append("\"");
        } else if (val instanceof Map<?, ?> map) {
            writeMap(sb, map, depth);
        } else if (val instanceof Collection<?> col) {
            writeCollection(sb, col, depth);
        } else if (val.getClass().isArray()) {
            writeArray(sb, (Object[]) val, depth);
        } else {
            sb.append("\"").append(escapeJson(val.toString())).append("\"");
        }
    }

    private void writeMap(StringBuilder sb, Map<?, ?> map, int depth) {
        String nl = pretty ? "\n" : "";
        String ind = pretty ? "  ".repeat(depth) : "";
        String childInd = pretty ? "  ".repeat(depth + 1) : "";

        sb.append("{").append(nl);
        int i = 0;
        int size = map.size();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append(childInd)
                    .append("\"").append(escapeJson(String.valueOf(entry.getKey()))).append("\": ");
            writeJsonValue(sb, entry.getValue(), depth + 1);
            if (++i < size) {
                sb.append(",");
            }
            sb.append(nl);
        }
        sb.append(ind).append("}");
    }

    private void writeCollection(StringBuilder sb, Collection<?> col, int depth) {
        String nl = pretty ? "\n" : "";
        String ind = pretty ? "  ".repeat(depth) : "";
        String childInd = pretty ? "  ".repeat(depth + 1) : "";

        sb.append("[").append(nl);
        int i = 0;
        int size = col.size();
        for (Object item : col) {
            sb.append(childInd);
            writeJsonValue(sb, item, depth + 1);
            if (++i < size) {
                sb.append(",");
            }
            sb.append(nl);
        }
        sb.append(ind).append("]");
    }

    private void writeArray(StringBuilder sb, Object[] arr, int depth) {
        String nl = pretty ? "\n" : "";
        String ind = pretty ? "  ".repeat(depth) : "";
        String childInd = pretty ? "  ".repeat(depth + 1) : "";

        sb.append("[").append(nl);
        for (int i = 0; i < arr.length; i++) {
            sb.append(childInd);
            writeJsonValue(sb, arr[i], depth + 1);
            if (i < arr.length - 1) {
                sb.append(",");
            }
            sb.append(nl);
        }
        sb.append(ind).append("]");
    }

    /**
     * Fast string escaper for JSON RFC 8259 compliance.
     */
    public static String escapeJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
