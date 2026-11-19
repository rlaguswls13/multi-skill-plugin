package com.msp.cborstream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Formats CBOR token streams into RFC 8949 Section 8 Diagnostic Notation
 * (e.g., {@code {1: "text", 2: [1, 2], 3: true, 4: null, 5: 1.5, 6: h'010203'}}).
 */
public final class CborDiagnosticFormatter {

    private CborDiagnosticFormatter() {}

    public static String format(byte[] cborBytes) throws IOException {
        Objects.requireNonNull(cborBytes, "cborBytes cannot be null");
        try (CborTokenizer tokenizer = new CborTokenizer(new ByteArrayInputStream(cborBytes))) {
            return format(tokenizer);
        }
    }

    public static String format(InputStream in) throws IOException {
        try (CborTokenizer tokenizer = new CborTokenizer(in)) {
            return format(tokenizer);
        }
    }

    public static String format(CborTokenizer tokenizer) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (tokenizer.hasNext()) {
            CborToken token = tokenizer.nextToken();
            if (token.type() == CborTokenType.END_OF_STREAM) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(formatToken(token, tokenizer));
        }
        return sb.toString();
    }

    private static String formatToken(CborToken token, CborTokenizer tokenizer) throws IOException {
        return switch (token.type()) {
            case UNSIGNED_INTEGER, NEGATIVE_INTEGER -> String.valueOf(token.value());
            case BYTE_STRING -> {
                if (token.isIndefinite()) {
                    StringBuilder bs = new StringBuilder("(_ ");
                    boolean first = true;
                    while (tokenizer.hasNext()) {
                        CborToken next = tokenizer.nextToken();
                        if (next.type() == CborTokenType.BREAK) break;
                        if (!first) bs.append(", ");
                        first = false;
                        bs.append(formatToken(next, tokenizer));
                    }
                    bs.append(")");
                    yield bs.toString();
                } else {
                    yield "h'" + HexFormat.of().formatHex(token.bytesValue()) + "'";
                }
            }
            case TEXT_STRING -> {
                if (token.isIndefinite()) {
                    StringBuilder ts = new StringBuilder("(_ ");
                    boolean first = true;
                    while (tokenizer.hasNext()) {
                        CborToken next = tokenizer.nextToken();
                        if (next.type() == CborTokenType.BREAK) break;
                        if (!first) ts.append(", ");
                        first = false;
                        ts.append(formatToken(next, tokenizer));
                    }
                    ts.append(")");
                    yield ts.toString();
                } else {
                    yield escapeString(token.textValue());
                }
            }
            case START_ARRAY -> {
                StringBuilder arr = new StringBuilder(token.isIndefinite() ? "[_ " : "[");
                if (token.isIndefinite()) {
                    boolean first = true;
                    while (tokenizer.hasNext()) {
                        CborToken next = tokenizer.nextToken();
                        if (next.type() == CborTokenType.BREAK) break;
                        if (!first) arr.append(", ");
                        first = false;
                        arr.append(formatToken(next, tokenizer));
                    }
                } else {
                    long len = token.length();
                    for (int i = 0; i < len; i++) {
                        if (i > 0) arr.append(", ");
                        CborToken item = tokenizer.nextToken();
                        arr.append(formatToken(item, tokenizer));
                    }
                }
                arr.append("]");
                yield arr.toString();
            }
            case START_MAP -> {
                StringBuilder map = new StringBuilder(token.isIndefinite() ? "{_ " : "{");
                if (token.isIndefinite()) {
                    boolean first = true;
                    while (tokenizer.hasNext()) {
                        CborToken key = tokenizer.nextToken();
                        if (key.type() == CborTokenType.BREAK) break;
                        if (!first) map.append(", ");
                        first = false;
                        map.append(formatToken(key, tokenizer)).append(": ");
                        CborToken val = tokenizer.nextToken();
                        map.append(formatToken(val, tokenizer));
                    }
                } else {
                    long len = token.length();
                    for (int i = 0; i < len; i++) {
                        if (i > 0) map.append(", ");
                        CborToken key = tokenizer.nextToken();
                        map.append(formatToken(key, tokenizer)).append(": ");
                        CborToken val = tokenizer.nextToken();
                        map.append(formatToken(val, tokenizer));
                    }
                }
                map.append("}");
                yield map.toString();
            }
            case TAG -> {
                long tag = token.tagValue();
                CborToken enclosed = tokenizer.nextToken();
                yield tag + "(" + formatToken(enclosed, tokenizer) + ")";
            }
            case BOOLEAN -> String.valueOf(token.booleanValue());
            case NULL -> "null";
            case UNDEFINED -> "undefined";
            case FLOAT -> {
                double d = token.doubleValue();
                if (Double.isNaN(d)) yield "NaN";
                if (Double.isInfinite(d)) yield (d > 0) ? "Infinity" : "-Infinity";
                yield String.valueOf(d);
            }
            case SIMPLE_VALUE -> "simple(" + token.value() + ")";
            case BREAK -> "break";
            case END_ARRAY, END_MAP, END_OF_STREAM -> "";
        };
    }

    private static String escapeString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c >= 0x7F && Character.isISOControl(c)) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
