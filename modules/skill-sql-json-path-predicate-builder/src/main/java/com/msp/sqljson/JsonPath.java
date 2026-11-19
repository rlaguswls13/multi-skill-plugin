package com.msp.sqljson;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, injection-safe representation of a JSON navigation path composed of
 * {@link JsonPathSegment.Property} and {@link JsonPathSegment.ArrayIndex} steps.
 */
public final class JsonPath {

    private static final JsonPath ROOT = new JsonPath(List.of());

    private final List<JsonPathSegment> segments;

    private JsonPath(List<JsonPathSegment> segments) {
        this.segments = List.copyOf(segments);
    }

    /**
     * Returns the empty root path ({@code $}).
     */
    public static JsonPath root() {
        return ROOT;
    }

    /**
     * Creates a path consisting solely of property segments.
     *
     * @param properties property names in navigation order
     * @return immutable {@link JsonPath}
     */
    public static JsonPath of(String... properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        List<JsonPathSegment> list = new ArrayList<>(properties.length);
        for (String property : properties) {
            list.add(new JsonPathSegment.Property(property));
        }
        return new JsonPath(list);
    }

    /**
     * Creates a path from an explicit list of segments.
     */
    public static JsonPath ofSegments(List<JsonPathSegment> segments) {
        Objects.requireNonNull(segments, "segments must not be null");
        return new JsonPath(segments);
    }

    /**
     * Parses a dot/bracket JSON path expression such as {@code "$.items[0].price"}
     * or {@code "user.address.city"}.
     *
     * @param expression path expression
     * @return parsed and validated {@link JsonPath}
     * @throws IllegalArgumentException if syntax or any segment is invalid
     */
    public static JsonPath parse(String expression) {
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("JSON path expression must not be null or empty");
        }
        String input = expression;
        if (input.equals("$")) {
            return ROOT;
        }
        int i = 0;
        if (input.charAt(0) == '$') {
            i = 1;
            if (i < input.length() && input.charAt(i) == '.') {
                i++;
            } else if (i < input.length() && input.charAt(i) != '[') {
                throw new IllegalArgumentException("Expected '.' or '[' after '$' in path: " + expression);
            }
        }

        List<JsonPathSegment> result = new ArrayList<>();
        int len = input.length();
        while (i < len) {
            char c = input.charAt(i);
            if (c == '[') {
                int close = input.indexOf(']', i + 1);
                if (close == -1 || close == i + 1) {
                    throw new IllegalArgumentException("Malformed array index in path: " + expression);
                }
                String indexStr = input.substring(i + 1, close);
                int idx;
                try {
                    idx = Integer.parseInt(indexStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Non-integer array index '" + indexStr + "' in path: " + expression, e);
                }
                result.add(new JsonPathSegment.ArrayIndex(idx));
                i = close + 1;
                if (i < len) {
                    if (input.charAt(i) == '.') {
                        i++;
                        if (i == len) {
                            throw new IllegalArgumentException("Trailing '.' in path: " + expression);
                        }
                    } else if (input.charAt(i) != '[') {
                        throw new IllegalArgumentException("Unexpected character '" + input.charAt(i) + "' after ']' in path: " + expression);
                    }
                }
            } else if (c == '.') {
                throw new IllegalArgumentException("Unexpected '.' at index " + i + " in path: " + expression);
            } else {
                int start = i;
                while (i < len && input.charAt(i) != '.' && input.charAt(i) != '[') {
                    i++;
                }
                String prop = input.substring(start, i);
                result.add(new JsonPathSegment.Property(prop));
                if (i < len && input.charAt(i) == '.') {
                    i++;
                    if (i == len) {
                        throw new IllegalArgumentException("Trailing '.' in path: " + expression);
                    }
                }
            }
        }
        return new JsonPath(result);
    }

    /**
     * Returns a new path with an appended object property segment.
     */
    public JsonPath property(String name) {
        List<JsonPathSegment> next = new ArrayList<>(segments.size() + 1);
        next.addAll(segments);
        next.add(new JsonPathSegment.Property(name));
        return new JsonPath(next);
    }

    /**
     * Returns a new path with an appended array index segment.
     */
    public JsonPath index(int index) {
        List<JsonPathSegment> next = new ArrayList<>(segments.size() + 1);
        next.addAll(segments);
        next.add(new JsonPathSegment.ArrayIndex(index));
        return new JsonPath(next);
    }

    public List<JsonPathSegment> segments() {
        return segments;
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public int size() {
        return segments.size();
    }

    /**
     * Formats this path in standard JSONPath dollar syntax (e.g. {@code $.items[0].price})
     * used by MySQL and SQLite.
     */
    public String toDollarPath() {
        StringBuilder sb = new StringBuilder("$");
        for (JsonPathSegment segment : segments) {
            switch (segment) {
                case JsonPathSegment.Property p -> sb.append('.').append(p.name());
                case JsonPathSegment.ArrayIndex idx -> sb.append('[').append(idx.index()).append(']');
            }
        }
        return sb.toString();
    }

    /**
     * Formats this path as a PostgreSQL text-array literal (e.g. {@code '{items,0,price}'})
     * for {@code #>} and {@code #>>} operators.
     */
    public String toPostgresPathArrayLiteral() {
        StringBuilder sb = new StringBuilder("'{");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            switch (segments.get(i)) {
                case JsonPathSegment.Property p -> sb.append(p.name());
                case JsonPathSegment.ArrayIndex idx -> sb.append(idx.index());
            }
        }
        sb.append("}'");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonPath other)) return false;
        return segments.equals(other.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        return toDollarPath();
    }
}
