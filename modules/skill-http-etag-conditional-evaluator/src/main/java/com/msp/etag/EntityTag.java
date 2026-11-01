package com.msp.etag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an HTTP Entity Tag (ETag) as defined in RFC 7232 Section 2.3.
 *
 * <p>An entity-tag consists of an opaque quoted string, possibly prefixed by
 * a weakness indicator ("W/"). It may also represent the wildcard wildcard ("*").</p>
 */
public final class EntityTag {

    private static final EntityTag WILDCARD = new EntityTag("*", false, true);

    private final String opaqueTag;
    private final boolean weak;
    private final boolean wildcard;

    private EntityTag(String opaqueTag, boolean weak, boolean wildcard) {
        this.opaqueTag = Objects.requireNonNull(opaqueTag, "opaqueTag cannot be null");
        this.weak = weak;
        this.wildcard = wildcard;
    }

    /**
     * Creates a strong entity tag with the given opaque string value (without quotes).
     */
    public static EntityTag strong(String opaqueTag) {
        return new EntityTag(stripQuotes(opaqueTag), false, false);
    }

    /**
     * Creates a weak entity tag (W/"...") with the given opaque string value.
     */
    public static EntityTag weak(String opaqueTag) {
        return new EntityTag(stripQuotes(opaqueTag), true, false);
    }

    /**
     * Returns the wildcard entity-tag ("*").
     */
    public static EntityTag wildcard() {
        return WILDCARD;
    }

    /**
     * Parses a single ETag string, e.g. {@code "xyz"}, {@code W/"xyz"}, or {@code *}.
     *
     * @param raw the raw header token
     * @return the parsed EntityTag
     * @throws IllegalArgumentException if the format is invalid
     */
    public static EntityTag parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("ETag string cannot be null");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("ETag string cannot be empty");
        }
        if ("*".equals(trimmed)) {
            return WILDCARD;
        }

        boolean isWeak = false;
        String content = trimmed;
        if (trimmed.startsWith("W/") || trimmed.startsWith("w/")) {
            isWeak = true;
            content = trimmed.substring(2).trim();
        }

        if (content.startsWith("\"") && content.endsWith("\"") && content.length() >= 2) {
            content = content.substring(1, content.length() - 1);
        }

        return new EntityTag(content, isWeak, false);
    }

    /**
     * Parses a comma-separated list of ETags from an HTTP header value (e.g. If-Match, If-None-Match).
     *
     * @param headerValue the raw header value
     * @return an unmodifiable list of parsed EntityTags
     */
    public static List<EntityTag> parseList(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Collections.emptyList();
        }

        List<EntityTag> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < headerValue.length(); i++) {
            char c = headerValue.charAt(i);
            if (c == '"') {
                insideQuotes = !insideQuotes;
                current.append(c);
            } else if (c == ',' && !insideQuotes) {
                String token = current.toString().trim();
                if (!token.isEmpty()) {
                    result.add(parse(token));
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            result.add(parse(remaining));
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Strong comparison function (RFC 7232 Section 2.3.2):
     * Two entity-tags are equivalent if neither is weak and their opaque tags match character-for-character.
     */
    public boolean strongCompare(EntityTag other) {
        if (other == null) {
            return false;
        }
        if (this.isWildcard() || other.isWildcard()) {
            return true;
        }
        if (this.weak || other.weak) {
            return false;
        }
        return this.opaqueTag.equals(other.opaqueTag);
    }

    /**
     * Weak comparison function (RFC 7232 Section 2.3.2):
     * Two entity-tags are equivalent if their opaque tags match character-for-character,
     * regardless of whether either or both are weak.
     */
    public boolean weakCompare(EntityTag other) {
        if (other == null) {
            return false;
        }
        if (this.isWildcard() || other.isWildcard()) {
            return true;
        }
        return this.opaqueTag.equals(other.opaqueTag);
    }

    public String getOpaqueTag() {
        return opaqueTag;
    }

    public boolean isWeak() {
        return weak;
    }

    public boolean isWildcard() {
        return wildcard;
    }

    /**
     * Returns formatted header string: {@code "opaque"}, {@code W/"opaque"}, or {@code *}.
     */
    public String format() {
        if (wildcard) {
            return "*";
        }
        if (weak) {
            return "W/\"" + opaqueTag + "\"";
        }
        return "\"" + opaqueTag + "\"";
    }

    private static String stripQuotes(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityTag entityTag = (EntityTag) o;
        return weak == entityTag.weak &&
                wildcard == entityTag.wildcard &&
                opaqueTag.equals(entityTag.opaqueTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(opaqueTag, weak, wildcard);
    }

    @Override
    public String toString() {
        return format();
    }
}
