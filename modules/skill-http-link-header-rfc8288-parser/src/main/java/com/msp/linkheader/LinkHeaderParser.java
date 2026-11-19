package com.msp.linkheader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Thread-safe RFC 8288 Web Linking ({@code Link}) HTTP header parser and formatter.
 *
 * <p>Supports:
 * <ul>
 *   <li>Multiple comma-separated link entries in a single header or across multiple headers</li>
 *   <li>Quoted-string parameters with {@code \"} and {@code \\} escapes, commas, and semicolons</li>
 *   <li>Unquoted token parameters and valueless flag parameters</li>
 *   <li>Standard parameters ({@code rel}, {@code anchor}, {@code rev}, {@code hreflang}, {@code media},
 *       {@code title}, {@code title*}, {@code type}) and custom extension parameters</li>
 * </ul>
 */
public final class LinkHeaderParser {

    private LinkHeaderParser() {
    }

    /**
     * Parses a single {@code Link} HTTP header value into an immutable list of {@link LinkEntry}.
     * Returns an empty list if {@code headerValue} is {@code null} or blank.
     *
     * @throws LinkHeaderException if the header syntax is malformed
     */
    public static List<LinkEntry> parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return List.of();
        }
        Scanner scanner = new Scanner(headerValue);
        List<LinkEntry> entries = new ArrayList<>();
        while (scanner.hasNext()) {
            scanner.skipWhitespaceAndCommas();
            if (!scanner.hasNext()) {
                break;
            }
            entries.add(scanner.readLinkEntry());
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * Parses multiple {@code Link} HTTP header lines in order.
     */
    public static List<LinkEntry> parseAll(List<String> headerValues) {
        if (headerValues == null || headerValues.isEmpty()) {
            return List.of();
        }
        List<LinkEntry> all = new ArrayList<>();
        for (String value : headerValues) {
            if (value != null && !value.isBlank()) {
                all.addAll(parse(value));
            }
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * Formats a list of {@link LinkEntry} instances into a single RFC 8288 {@code Link} header value.
     */
    public static String format(List<LinkEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        return entries.stream()
                .map(LinkEntry::toHeaderValue)
                .collect(Collectors.joining(", "));
    }

    private static final class Scanner {
        private final String input;
        private final int length;
        private int pos;

        Scanner(String input) {
            this.input = input;
            this.length = input.length();
            this.pos = 0;
        }

        boolean hasNext() {
            return pos < length;
        }

        void skipWhitespace() {
            while (pos < length && isOws(input.charAt(pos))) {
                pos++;
            }
        }

        void skipWhitespaceAndCommas() {
            while (pos < length && (isOws(input.charAt(pos)) || input.charAt(pos) == ',')) {
                pos++;
            }
        }

        LinkEntry readLinkEntry() {
            skipWhitespace();
            if (pos >= length || input.charAt(pos) != '<') {
                throw new LinkHeaderException("Expected '<' at start of link target URI", pos);
            }
            int uriStart = ++pos;
            int uriEnd = input.indexOf('>', uriStart);
            if (uriEnd < 0) {
                throw new LinkHeaderException("Unterminated link target URI (missing '>')", uriStart - 1);
            }
            String targetUri = input.substring(uriStart, uriEnd).trim();
            pos = uriEnd + 1;

            Map<String, List<String>> params = new LinkedHashMap<>();
            while (pos < length) {
                skipWhitespace();
                if (pos >= length) {
                    break;
                }
                char c = input.charAt(pos);
                if (c == ',') {
                    pos++;
                    break;
                }
                if (c != ';') {
                    throw new LinkHeaderException("Expected ';' or ',' after link entry, found '" + c + "'", pos);
                }
                pos++; // consume ';'
                skipWhitespace();
                if (pos >= length || input.charAt(pos) == ',' || input.charAt(pos) == ';') {
                    continue; // tolerate trailing/empty semicolon
                }

                String paramName = readToken();
                if (paramName.isEmpty()) {
                    throw new LinkHeaderException("Expected parameter name after ';'", pos);
                }
                String normalizedName = paramName.toLowerCase(Locale.ROOT);
                skipWhitespace();

                String paramValue = "";
                if (pos < length && input.charAt(pos) == '=') {
                    pos++; // consume '='
                    skipWhitespace();
                    if (pos < length && input.charAt(pos) == '"') {
                        paramValue = readQuotedString();
                    } else {
                        paramValue = readUnquotedValue();
                    }
                }
                params.computeIfAbsent(normalizedName, k -> new ArrayList<>()).add(paramValue);
            }

            return new LinkEntry(targetUri, params);
        }

        private String readToken() {
            int start = pos;
            while (pos < length && isTokenChar(input.charAt(pos))) {
                pos++;
            }
            return input.substring(start, pos);
        }

        private String readQuotedString() {
            int quoteStart = pos;
            pos++; // consume opening '"'
            StringBuilder sb = new StringBuilder();
            while (pos < length) {
                char c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= length) {
                        throw new LinkHeaderException("Unterminated escape sequence in quoted string", pos - 1);
                    }
                    sb.append(input.charAt(pos++));
                } else {
                    sb.append(c);
                }
            }
            throw new LinkHeaderException("Unterminated quoted string", quoteStart);
        }

        private String readUnquotedValue() {
            int start = pos;
            while (pos < length) {
                char c = input.charAt(pos);
                if (c == ';' || c == ',') {
                    break;
                }
                pos++;
            }
            return input.substring(start, pos).trim();
        }

        private static boolean isOws(char c) {
            return c == ' ' || c == '\t' || c == '\r' || c == '\n';
        }

        private static boolean isTokenChar(char c) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                return true;
            }
            return switch (c) {
                case '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~' -> true;
                default -> false;
            };
        }
    }
}
