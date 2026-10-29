package com.msp.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable HTTP response representation with convenient body extraction and status checks.
 */
public record LiteHttpResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] bodyBytes
) {

    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)\\bcharset=\\s*\"?([^\\s;\"]+)");

    public LiteHttpResponse {
        headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        bodyBytes = bodyBytes != null ? bodyBytes.clone() : new byte[0];
    }

    /**
     * Returns the response body decoded as a UTF-8 string (or charset declared in Content-Type).
     */
    public String body() {
        if (bodyBytes.length == 0) {
            return "";
        }
        Charset charset = detectCharset().orElse(StandardCharsets.UTF_8);
        return new String(bodyBytes, charset);
    }

    /**
     * Returns a copy of the raw response bytes.
     */
    public byte[] bodyAsBytes() {
        return bodyBytes.clone();
    }

    /**
     * Looks up the first header value matching the given name (case-insensitive).
     */
    public Optional<String> header(String name) {
        if (name == null || headers.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                return (values != null && !values.isEmpty()) ? Optional.ofNullable(values.getFirst()) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Looks up all header values matching the given name (case-insensitive).
     */
    public List<String> headers(String name) {
        if (name == null || headers.isEmpty()) {
            return List.of();
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue() != null ? Collections.unmodifiableList(entry.getValue()) : List.of();
            }
        }
        return List.of();
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean is1xxInformational() {
        return statusCode >= 100 && statusCode < 200;
    }

    public boolean is2xxSuccessful() {
        return isSuccess();
    }

    public boolean is3xxRedirection() {
        return statusCode >= 300 && statusCode < 400;
    }

    public boolean is4xxClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean is5xxServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    public boolean isClientError() {
        return is4xxClientError();
    }

    public boolean isServerError() {
        return is5xxServerError();
    }

    private Optional<Charset> detectCharset() {
        return header("Content-Type").flatMap(ct -> {
            Matcher matcher = CHARSET_PATTERN.matcher(ct);
            if (matcher.find()) {
                try {
                    return Optional.of(Charset.forName(matcher.group(1)));
                } catch (Exception ignored) {
                    // Fall back to default
                }
            }
            return Optional.empty();
        });
    }
}
