package com.msp.byteunit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Human-readable byte size formatter and parser supporting both IEC (1024-based binary: KiB, MiB, GiB...)
 * and SI (1000-based decimal: KB, MB, GB...) standards.
 */
public final class ByteUnitFormatter {

    private static final Pattern PATTERN = Pattern.compile("^([+-]?\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]+)?$");

    private static final Map<String, Long> MULTIPLIERS = Map.ofEntries(
            // Bytes
            Map.entry("b", 1L),
            Map.entry("byte", 1L),
            Map.entry("bytes", 1L),

            // IEC (Base 1024)
            Map.entry("kib", 1024L),
            Map.entry("mib", 1024L * 1024L),
            Map.entry("gib", 1024L * 1024L * 1024L),
            Map.entry("tib", 1024L * 1024L * 1024L * 1024L),
            Map.entry("pib", 1024L * 1024L * 1024L * 1024L * 1024L),
            Map.entry("eib", 1024L * 1024L * 1024L * 1024L * 1024L * 1024L),

            // SI (Base 1000)
            Map.entry("kb", 1_000L),
            Map.entry("mb", 1_000_000L),
            Map.entry("gb", 1_000_000_000L),
            Map.entry("tb", 1_000_000_000_000L),
            Map.entry("pb", 1_000_000_000_000_000L),
            Map.entry("eb", 1_000_000_000_000_000_000L),

            // Common abbreviations
            Map.entry("k", 1_000L),
            Map.entry("m", 1_000_000L),
            Map.entry("g", 1_000_000_000L),
            Map.entry("t", 1_000_000_000_000L),
            Map.entry("p", 1_000_000_000_000_000L),
            Map.entry("e", 1_000_000_000_000_000_000L)
    );

    private ByteUnitFormatter() {
        // Utility class
    }

    /**
     * Formats the byte count using default IEC standard (base 1024) with 2 decimal places.
     *
     * @param bytes the byte count
     * @return formatted string (e.g. "1.50 MiB", "0 B", "-2.30 GiB")
     */
    public static String format(long bytes) {
        return format(bytes, ByteUnitStandard.IEC, 2);
    }

    /**
     * Formats the byte count using the specified standard and decimal places.
     *
     * @param bytes         the byte count (supports negative values and boundary values)
     * @param standard      the measurement standard (IEC or SI)
     * @param decimalPlaces the number of fractional decimal places to display (non-negative)
     * @return formatted human-readable byte string
     * @throws IllegalArgumentException if decimalPlaces is negative
     */
    public static String format(long bytes, ByteUnitStandard standard, int decimalPlaces) {
        Objects.requireNonNull(standard, "standard cannot be null");
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("decimalPlaces must be non-negative: " + decimalPlaces);
        }

        if (bytes == 0) {
            return "0 B";
        }

        boolean negative = bytes < 0;
        // Avoid overflow on Long.MIN_VALUE by converting directly to double
        double absBytes = negative ? -(double) bytes : (double) bytes;

        long base = standard.base();
        String[] units = standard.units();

        int unitIndex = 0;
        while (absBytes >= base && unitIndex < units.length - 1) {
            absBytes /= base;
            unitIndex++;
        }

        String unit = units[unitIndex];
        String formattedNumber;
        if (unitIndex == 0) {
            // Integer byte count
            formattedNumber = String.format(Locale.ROOT, "%d", (long) absBytes);
        } else {
            formattedNumber = String.format(Locale.ROOT, "%." + decimalPlaces + "f", absBytes);
        }

        String result = formattedNumber + " " + unit;
        return negative ? "-" + result : result;
    }

    /**
     * Parses a human-readable byte size string into an exact byte count.
     * <p>
     * Supports formats like "1.5 GiB", "500MB", "1024 B", "2.3KiB", "-10 MiB".
     *
     * @param text the text to parse
     * @return parsed byte count
     * @throws IllegalArgumentException if input is null, empty, or not a valid byte size string
     * @throws ArithmeticException      if the computed byte count exceeds 64-bit signed long capacity
     */
    public static long parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Input text cannot be null or blank");
        }

        String trimmed = text.trim();
        Matcher matcher = PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid byte unit format: " + text);
        }

        String numberStr = matcher.group(1);
        String unitStr = matcher.group(2);

        long multiplier = 1L;
        if (unitStr != null && !unitStr.isEmpty()) {
            String lowerUnit = unitStr.toLowerCase(Locale.ROOT);
            Long factor = MULTIPLIERS.get(lowerUnit);
            if (factor == null) {
                throw new IllegalArgumentException("Unknown byte unit: " + unitStr);
            }
            multiplier = factor;
        }

        try {
            BigDecimal bdNumber = new BigDecimal(numberStr);
            BigDecimal totalBytes = bdNumber.multiply(BigDecimal.valueOf(multiplier));

            if (totalBytes.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 ||
                totalBytes.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0) {
                throw new ArithmeticException("Byte count exceeds 64-bit signed long capacity: " + text);
            }

            return totalBytes.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in byte size: " + text, e);
        }
    }
}
