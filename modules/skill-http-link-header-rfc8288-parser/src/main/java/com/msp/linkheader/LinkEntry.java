package com.msp.linkheader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of a single RFC 8288 Web Link entry
 * ({@code <target-uri>; param1=val1; param2="val2"}).
 *
 * <p>Parameter names are normalized to lowercase per RFC 8288 Section 3.3.
 * Parameter ordering and multi-valued parameters (such as {@code hreflang}) are preserved.
 *
 * @param targetUri  target IRI/URI reference enclosed in angle brackets
 * @param parameters unmodifiable map of lowercase parameter name to non-empty list of values
 */
public record LinkEntry(String targetUri, Map<String, List<String>> parameters) {

    public LinkEntry {
        Objects.requireNonNull(targetUri, "targetUri must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        if (targetUri.indexOf('>') >= 0 || targetUri.indexOf('\r') >= 0 || targetUri.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("targetUri contains illegal characters: " + targetUri);
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            String key = normalizeParamName(entry.getKey());
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                List<String> normalizedValues = new ArrayList<>(values.size());
                for (String v : values) {
                    normalizedValues.add(v == null ? "" : v);
                }
                copy.merge(key, normalizedValues, (existing, added) -> {
                    List<String> merged = new ArrayList<>(existing.size() + added.size());
                    merged.addAll(existing);
                    merged.addAll(added);
                    return merged;
                });
            }
        }
        Map<String, List<String>> immutable = new LinkedHashMap<>(copy.size());
        for (Map.Entry<String, List<String>> e : copy.entrySet()) {
            immutable.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        parameters = Collections.unmodifiableMap(immutable);
    }

    /**
     * Starts a fluent builder for a {@link LinkEntry}.
     */
    public static Builder builder(String targetUri) {
        return new Builder(targetUri);
    }

    /**
     * Returns all relation types declared in the first {@code rel} parameter, split on ASCII whitespace.
     * Registered relation types (without {@code :}) are lowercased per RFC 8288 Section 2.1.1.
     */
    public List<String> rels() {
        Optional<String> rawRel = parameter("rel");
        if (rawRel.isEmpty() || rawRel.get().isBlank()) {
            return List.of();
        }
        String[] tokens = rawRel.get().trim().split("\\s+");
        List<String> result = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (!token.isEmpty()) {
                result.add(token.contains(":") ? token : token.toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Checks whether this link carries the specified relation type (case-insensitive for registered rels).
     */
    public boolean hasRel(String rel) {
        if (rel == null || rel.isBlank()) {
            return false;
        }
        String needle = rel.contains(":") ? rel : rel.toLowerCase(Locale.ROOT);
        return rels().contains(needle);
    }

    /**
     * Returns the primary (first) relation type, if any.
     */
    public Optional<String> firstRel() {
        List<String> list = rels();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<String> anchor() {
        return parameter("anchor");
    }

    public Optional<String> rev() {
        return parameter("rev");
    }

    public List<String> hreflang() {
        return parameters("hreflang");
    }

    public Optional<String> media() {
        return parameter("media");
    }

    /**
     * Returns the preferred human-readable title: decoded {@code title*} (RFC 8187) when present and valid,
     * falling back to {@code title} per RFC 8288 Section 3.4.1.
     */
    public Optional<String> title() {
        Optional<ExtValue> star = titleStar();
        if (star.isPresent()) {
            return Optional.of(star.get().value());
        }
        return rawTitle();
    }

    /**
     * Returns the raw {@code title} parameter value (ignoring {@code title*}).
     */
    public Optional<String> rawTitle() {
        return parameter("title");
    }

    /**
     * Parses and returns the RFC 8187 {@code title*} extended parameter if present and well-formed.
     */
    public Optional<ExtValue> titleStar() {
        Optional<String> raw = parameter("title*");
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ExtValue.parse(raw.get()));
        } catch (LinkHeaderException e) {
            return Optional.empty();
        }
    }

    public Optional<String> type() {
        return parameter("type");
    }

    /**
     * Returns the first value of the given parameter name (case-insensitive).
     */
    public Optional<String> parameter(String name) {
        List<String> values = parameters(name);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /**
     * Returns all values of the given parameter name (case-insensitive).
     */
    public List<String> parameters(String name) {
        if (name == null) {
            return List.of();
        }
        List<String> values = parameters.get(name.toLowerCase(Locale.ROOT));
        return values == null ? List.of() : values;
    }

    /**
     * Serializes this link entry into RFC 8288 header syntax.
     */
    public String toHeaderValue() {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(targetUri).append('>');
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                sb.append("; ").append(name);
                if (!value.isEmpty() || requiresQuotedEmpty(name)) {
                    sb.append('=');
                    if (name.endsWith("*")) {
                        sb.append(value);
                    } else if (shouldQuote(name, value)) {
                        sb.append('"').append(escapeQuoted(value)).append('"');
                    } else {
                        sb.append(value);
                    }
                }
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toHeaderValue();
    }

    private static boolean requiresQuotedEmpty(String name) {
        return "title".equals(name) || "rel".equals(name);
    }

    private static boolean shouldQuote(String name, String value) {
        if ("rel".equals(name) || "title".equals(name) || "anchor".equals(name) || "media".equals(name)) {
            return true;
        }
        if (value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isTokenChar(value.charAt(i))) {
                return true;
            }
        }
        return false;
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

    private static String escapeQuoted(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String normalizeParamName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name must not be blank");
        }
        String trimmed = name.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            if (!isTokenChar(trimmed.charAt(i))) {
                throw new IllegalArgumentException("Invalid parameter name token: " + name);
            }
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Fluent builder for {@link LinkEntry}.
     */
    public static final class Builder {
        private final String targetUri;
        private final Map<String, List<String>> params = new LinkedHashMap<>();

        private Builder(String targetUri) {
            this.targetUri = Objects.requireNonNull(targetUri, "targetUri must not be null");
        }

        public Builder rel(String... rels) {
            Objects.requireNonNull(rels, "rels must not be null");
            String joined = String.join(" ", Arrays.stream(rels)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());
            if (!joined.isEmpty()) {
                param("rel", joined);
            }
            return this;
        }

        public Builder anchor(String anchor) {
            return param("anchor", anchor);
        }

        public Builder rev(String rev) {
            return param("rev", rev);
        }

        public Builder hreflang(String hreflang) {
            return param("hreflang", hreflang);
        }

        public Builder media(String media) {
            return param("media", media);
        }

        public Builder title(String title) {
            return param("title", title);
        }

        public Builder titleStar(String language, String title) {
            return param("title*", ExtValue.utf8(language, title).encode());
        }

        public Builder titleStar(ExtValue extValue) {
            Objects.requireNonNull(extValue, "extValue must not be null");
            return param("title*", extValue.encode());
        }

        public Builder type(String mediaType) {
            return param("type", mediaType);
        }

        public Builder param(String name, String value) {
            String key = normalizeParamName(name);
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value == null ? "" : value);
            return this;
        }

        public LinkEntry build() {
            return new LinkEntry(targetUri, params);
        }
    }
}
