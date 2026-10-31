package com.msp.jsonpatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 6901 JSON Pointer implementation.
 * <p>
 * Supports reference token escaping ({@code ~0} -> {@code ~}, {@code ~1} -> {@code /})
 * and hierarchical navigation over standard Java {@link Map} and {@link List} JSON structures.
 * </p>
 */
public final class JsonPointer {

    private static final JsonPointer ROOT = new JsonPointer("", Collections.emptyList());

    private final String raw;
    private final List<String> tokens;

    private JsonPointer(String raw, List<String> tokens) {
        this.raw = raw;
        this.tokens = Collections.unmodifiableList(tokens);
    }

    /**
     * Compiles a raw JSON Pointer string into a {@link JsonPointer}.
     *
     * @param rawPointer JSON pointer string (e.g., {@code "/foo/0/bar"}, {@code ""})
     * @return parsed JsonPointer
     */
    public static JsonPointer of(String rawPointer) {
        if (rawPointer == null || rawPointer.isEmpty()) {
            return ROOT;
        }

        if (!rawPointer.startsWith("/")) {
            throw new JsonPatchException("JSON Pointer must start with '/' or be empty: '" + rawPointer + "'");
        }

        String[] parts = rawPointer.substring(1).split("/", -1);
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            tokens.add(unescape(part));
        }

        return new JsonPointer(rawPointer, tokens);
    }

    public static JsonPointer compile(String rawPointer) {
        return of(rawPointer);
    }

    public static JsonPointer root() {
        return ROOT;
    }

    public String raw() {
        return raw;
    }

    public List<String> tokens() {
        return tokens;
    }

    public boolean isRoot() {
        return tokens.isEmpty();
    }

    public int tokenCount() {
        return tokens.size();
    }

    public String lastToken() {
        if (tokens.isEmpty()) {
            throw new JsonPatchException("Root pointer has no tokens");
        }
        return tokens.getLast();
    }

    /**
     * Returns the parent pointer (pointer without the last reference token).
     */
    public JsonPointer parent() {
        if (isRoot()) {
            throw new JsonPatchException("Root pointer has no parent");
        }
        if (tokens.size() == 1) {
            return ROOT;
        }
        int lastSlash = raw.lastIndexOf('/');
        String parentRaw = raw.substring(0, lastSlash);
        List<String> parentTokens = new ArrayList<>(tokens.subList(0, tokens.size() - 1));
        return new JsonPointer(parentRaw, parentTokens);
    }

    /**
     * Checks if this pointer is a strict descendant of another pointer.
     */
    public boolean isProperChildOf(JsonPointer other) {
        if (other == null || this.tokens.size() <= other.tokens.size()) {
            return false;
        }
        for (int i = 0; i < other.tokens.size(); i++) {
            if (!this.tokens.get(i).equals(other.tokens.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates this pointer against the given root document and returns the referenced value.
     */
    @SuppressWarnings("unchecked")
    public Object evaluate(Object root) {
        if (isRoot()) {
            return root;
        }

        Object current = root;
        for (String token : tokens) {
            if (current == null) {
                throw new JsonPatchException("Cannot dereference token '" + token + "' on null value in: " + raw);
            }

            if (current instanceof Map<?, ?> map) {
                if (!map.containsKey(token)) {
                    throw new JsonPatchException("Key not found: '" + token + "' in pointer: " + raw);
                }
                current = map.get(token);
            } else if (current instanceof List<?> list) {
                int index = parseIndex(token, list.size());
                if (index < 0 || index >= list.size()) {
                    throw new JsonPatchException("Index out of bounds: " + index + " for size " + list.size() + " in: " + raw);
                }
                current = list.get(index);
            } else {
                throw new JsonPatchException("Cannot evaluate token '" + token + "' on scalar: " + current.getClass().getSimpleName());
            }
        }
        return current;
    }

    /**
     * Checks whether this pointer points to an existing element in the document.
     */
    public boolean exists(Object root) {
        try {
            evaluate(root);
            return true;
        } catch (JsonPatchException e) {
            return false;
        }
    }

    public static int parseIndex(String token, int listSize) {
        if (token == null || token.isEmpty()) {
            throw new JsonPatchException("List index cannot be empty");
        }
        // RFC 6901: Leading zeros are not permitted unless the token is exactly "0"
        if (token.length() > 1 && token.startsWith("0")) {
            throw new JsonPatchException("Invalid list index with leading zeros: " + token);
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new JsonPatchException("Invalid list index: '" + token + "'", e);
        }
    }

    public static String unescape(String token) {
        // Must replace ~1 with / first, then ~0 with ~
        return token.replace("~1", "/").replace("~0", "~");
    }

    public static String escape(String token) {
        // Must replace ~ with ~0 first, then / with ~1
        return token.replace("~", "~0").replace("/", "~1");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonPointer that)) return false;
        return Objects.equals(tokens, that.tokens);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tokens);
    }

    @Override
    public String toString() {
        return raw;
    }
}
