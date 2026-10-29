package com.msp.csv;

import java.util.*;

/**
 * An immutable record representing a parsed row in a CSV/TSV document.
 */
public record CsvRow(long rowNumber, List<String> values, Map<String, Integer> headerMap) {

    public CsvRow {
        values = values != null ? List.copyOf(values) : List.of();
        headerMap = headerMap != null ? Collections.unmodifiableMap(new HashMap<>(headerMap)) : Collections.emptyMap();
    }

    /**
     * Gets column value by 0-based column index.
     * Returns {@code null} if index is out of bounds.
     */
    public String get(int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    /**
     * Gets column value by case-insensitive header name.
     * Returns {@code null} if the header does not exist.
     */
    public String get(String header) {
        if (header == null) {
            return null;
        }
        Integer idx = headerMap.get(header);
        if (idx == null) {
            // Case-insensitive lookup fallback
            for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(header)) {
                    idx = entry.getValue();
                    break;
                }
            }
        }
        return idx != null ? get(idx) : null;
    }

    /**
     * Returns column value wrapped in an {@link Optional}.
     */
    public Optional<String> getOptional(String header) {
        return Optional.ofNullable(get(header)).filter(s -> !s.isBlank());
    }

    /**
     * Gets integer value by header name, or default if missing/invalid.
     */
    public int getAsInt(String header, int defaultValue) {
        String val = get(header);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getAsInt(String header) {
        String val = get(header);
        if (val == null || val.isBlank()) {
            throw new NoSuchElementException("Header '" + header + "' not found or empty");
        }
        return Integer.parseInt(val.trim());
    }

    public long getAsLong(String header, long defaultValue) {
        String val = get(header);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getAsLong(String header) {
        String val = get(header);
        if (val == null || val.isBlank()) {
            throw new NoSuchElementException("Header '" + header + "' not found or empty");
        }
        return Long.parseLong(val.trim());
    }

    public double getAsDouble(String header, double defaultValue) {
        String val = get(header);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getAsDouble(String header) {
        String val = get(header);
        if (val == null || val.isBlank()) {
            throw new NoSuchElementException("Header '" + header + "' not found or empty");
        }
        return Double.parseDouble(val.trim());
    }

    public boolean getAsBoolean(String header) {
        String val = get(header);
        if (val == null) {
            return false;
        }
        String trimmed = val.trim().toLowerCase();
        return "true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed) || "y".equals(trimmed);
    }

    /**
     * Number of columns in this row.
     */
    public int size() {
        return values.size();
    }

    /**
     * Checks if this row has an associated header mapping.
     */
    public boolean hasHeader(String header) {
        return header != null && (headerMap.containsKey(header) || headerMap.keySet().stream().anyMatch(header::equalsIgnoreCase));
    }

    /**
     * Converts this row into a Map of header name to value.
     */
    public Map<String, String> toMap() {
        if (headerMap.isEmpty()) {
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < values.size(); i++) {
                map.put("col_" + i, values.get(i));
            }
            return map;
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
            map.put(entry.getKey(), get(entry.getValue()));
        }
        return map;
    }
}
