package com.msp.jsonflatten;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, zero-dependency utility for flattening and unflattening nested JSON-like
 * Map/List structures using dot and array index notation.
 * <p>
 * Example:
 * <pre>{@code
 * Map<String, Object> nested = Map.of(
 *     "user", Map.of("address", List.of("Seoul", "Gangnam"))
 * );
 *
 * Map<String, Object> flat = JsonFlattener.flatten(nested);
 * // Produces:
 * // { "user.address[0]": "Seoul", "user.address[1]": "Gangnam" }
 *
 * Map<String, Object> reconstructed = JsonFlattener.unflatten(flat);
 * // Restores original Map/List structure
 * }</pre>
 */
public final class JsonFlattener {

    public static final String DEFAULT_SEPARATOR = ".";
    private static final Pattern BRACKET_INDEX_PATTERN = Pattern.compile("\\[(\\d+)\\]");

    private final String separator;

    /**
     * Creates a flattener with the default dot ({@code "."}) separator.
     */
    public JsonFlattener() {
        this(DEFAULT_SEPARATOR);
    }

    /**
     * Creates a flattener with a custom separator.
     *
     * @param separator the key delimiter (e.g. "." or "/")
     */
    public JsonFlattener(String separator) {
        this.separator = Objects.requireNonNull(separator, "separator must not be null");
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("separator must not be empty");
        }
    }

    /**
     * Creates a flattener with a custom separator.
     */
    public static JsonFlattener withSeparator(String separator) {
        return new JsonFlattener(separator);
    }

    /**
     * Flattens a nested Map using the default dot separator.
     */
    public static Map<String, Object> flatten(Map<String, ?> nested) {
        return new JsonFlattener().flattenMap(nested);
    }

    /**
     * Unflattens a flat Map using the default dot separator.
     */
    public static Map<String, Object> unflatten(Map<String, ?> flat) {
        return new JsonFlattener().unflatMap(flat);
    }

    /**
     * Flattens the given nested Map using this instance's separator.
     */
    public Map<String, Object> flattenMap(Map<String, ?> nested) {
        if (nested == null || nested.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        flattenInternal("", nested, result);
        return result;
    }

    private void flattenInternal(String prefix, Object value, Map<String, Object> result) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                if (!prefix.isEmpty()) {
                    result.put(prefix, Collections.emptyMap());
                }
            } else {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    String nextKey = prefix.isEmpty() ? key : prefix + separator + key;
                    flattenInternal(nextKey, entry.getValue(), result);
                }
            }
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                if (!prefix.isEmpty()) {
                    result.put(prefix, Collections.emptyList());
                }
            } else {
                for (int i = 0; i < list.size(); i++) {
                    String nextKey = prefix + "[" + i + "]";
                    flattenInternal(nextKey, list.get(i), result);
                }
            }
        } else {
            result.put(prefix, value);
        }
    }

    /**
     * Unflattens the given flat Map back into nested Maps and Lists.
     */
    public Map<String, Object> unflatMap(Map<String, ?> flat) {
        if (flat == null || flat.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> root = new LinkedHashMap<>();

        for (Map.Entry<String, ?> entry : flat.entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();

            List<Token> tokens = parsePath(path);
            if (tokens.isEmpty()) {
                continue;
            }

            insertValue(root, tokens, 0, value);
        }

        return root;
    }

    @SuppressWarnings("unchecked")
    private void insertValue(Object currentContainer, List<Token> tokens, int tokenIndex, Object leafValue) {
        Token token = tokens.get(tokenIndex);
        boolean isLast = (tokenIndex == tokens.size() - 1);

        if (token instanceof PropertyToken(String propName)) {
            if (!(currentContainer instanceof Map)) {
                throw new IllegalStateException("Expected Map container for property token: " + propName);
            }
            Map<String, Object> map = (Map<String, Object>) currentContainer;

            if (isLast) {
                map.put(propName, leafValue);
            } else {
                Token nextToken = tokens.get(tokenIndex + 1);
                Object child = map.get(propName);
                if (nextToken instanceof IndexToken) {
                    if (!(child instanceof List)) {
                        child = new ArrayList<>();
                        map.put(propName, child);
                    }
                } else {
                    if (!(child instanceof Map)) {
                        child = new LinkedHashMap<String, Object>();
                        map.put(propName, child);
                    }
                }
                insertValue(child, tokens, tokenIndex + 1, leafValue);
            }
        } else if (token instanceof IndexToken(int index)) {
            if (!(currentContainer instanceof List)) {
                throw new IllegalStateException("Expected List container for index token: " + index);
            }
            List<Object> list = (List<Object>) currentContainer;

            // Ensure list capacity up to index
            while (list.size() <= index) {
                list.add(null);
            }

            if (isLast) {
                list.set(index, leafValue);
            } else {
                Token nextToken = tokens.get(tokenIndex + 1);
                Object child = list.get(index);
                if (nextToken instanceof IndexToken) {
                    if (!(child instanceof List)) {
                        child = new ArrayList<>();
                        list.set(index, child);
                    }
                } else {
                    if (!(child instanceof Map)) {
                        child = new LinkedHashMap<String, Object>();
                        list.set(index, child);
                    }
                }
                insertValue(child, tokens, tokenIndex + 1, leafValue);
            }
        }
    }

    private List<Token> parsePath(String path) {
        List<Token> tokens = new ArrayList<>();
        String[] segments = path.split(Pattern.quote(separator), -1);

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            int firstBracket = segment.indexOf('[');
            if (firstBracket < 0) {
                tokens.add(new PropertyToken(segment));
            } else {
                String propName = segment.substring(0, firstBracket);
                if (!propName.isEmpty()) {
                    tokens.add(new PropertyToken(propName));
                }
                Matcher matcher = BRACKET_INDEX_PATTERN.matcher(segment.substring(firstBracket));
                while (matcher.find()) {
                    tokens.add(new IndexToken(Integer.parseInt(matcher.group(1))));
                }
            }
        }
        return tokens;
    }

    private sealed interface Token permits PropertyToken, IndexToken {
    }

    private record PropertyToken(String name) implements Token {
    }

    private record IndexToken(int index) implements Token {
    }
}
