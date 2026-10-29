package com.msp.convert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Null-safe, exception-resilient conversion utility from String to primitives, wrappers,
 * enums, dates, and lists with customizable fallback defaults and strict or lenient modes.
 */
public final class TypeConverters {

    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "y", "on", "t");
    private static final Set<String> FALSE_VALUES = Set.of("false", "0", "no", "n", "off", "f");

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                    // 2026-10-30
            DateTimeFormatter.ofPattern("yyyyMMdd"),             // 20261030
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),           // 2026/10/30
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),           // 2026.10.30
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),           // 30-10-2026
            DateTimeFormatter.ofPattern("dd/MM/yyyy")            // 30/10/2026
    );

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,               // 2026-10-30T15:30:00
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),  // 2026-10-30 15:30:00
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),  // 2026/10/30 15:30:00
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),  // 2026.10.30 15:30:00
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")        // 20261030153000
    );

    private TypeConverters() {
    }

    // --- Integer ---

    public static Integer toInteger(String str, Integer defaultVal) {
        if (str == null || str.isBlank()) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static int toInt(String str, int defaultVal) {
        Integer res = toInteger(str, defaultVal);
        return res != null ? res : defaultVal;
    }

    public static Integer toIntegerOrThrow(String str) {
        if (str == null || str.isBlank()) {
            throw new TypeCastException(str, Integer.class);
        }
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            throw new TypeCastException(str, Integer.class, e);
        }
    }

    // --- Long ---

    public static Long toLong(String str, Long defaultVal) {
        if (str == null || str.isBlank()) {
            return defaultVal;
        }
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static long toLong(String str, long defaultVal) {
        Long res = toLong(str, Long.valueOf(defaultVal));
        return res != null ? res : defaultVal;
    }

    public static Long toLongOrThrow(String str) {
        if (str == null || str.isBlank()) {
            throw new TypeCastException(str, Long.class);
        }
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            throw new TypeCastException(str, Long.class, e);
        }
    }

    // --- Double ---

    public static Double toDouble(String str, Double defaultVal) {
        if (str == null || str.isBlank()) {
            return defaultVal;
        }
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static double toDouble(String str, double defaultVal) {
        Double res = toDouble(str, Double.valueOf(defaultVal));
        return res != null ? res : defaultVal;
    }

    public static Double toDoubleOrThrow(String str) {
        if (str == null || str.isBlank()) {
            throw new TypeCastException(str, Double.class);
        }
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            throw new TypeCastException(str, Double.class, e);
        }
    }

    // --- Float ---

    public static Float toFloat(String str, Float defaultVal) {
        if (str == null || str.isBlank()) {
            return defaultVal;
        }
        try {
            return Float.parseFloat(str.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static float toFloat(String str, float defaultVal) {
        Float res = toFloat(str, Float.valueOf(defaultVal));
        return res != null ? res : defaultVal;
    }

    public static Float toFloatOrThrow(String str) {
        if (str == null || str.isBlank()) {
            throw new TypeCastException(str, Float.class);
        }
        try {
            return Float.parseFloat(str.trim());
        } catch (NumberFormatException e) {
            throw new TypeCastException(str, Float.class, e);
        }
    }

    // --- Boolean ---

    public static Boolean toBoolean(String str, Boolean defaultVal) {
        if (str == null || str.isBlank()) {
            return defaultVal;
        }
        String normalized = str.trim().toLowerCase();
        if (TRUE_VALUES.contains(normalized)) {
            return Boolean.TRUE;
        }
        if (FALSE_VALUES.contains(normalized)) {
            return Boolean.FALSE;
        }
        return defaultVal;
    }

    public static boolean toBoolean(String str, boolean defaultVal) {
        Boolean res = toBoolean(str, Boolean.valueOf(defaultVal));
        return res != null ? res : defaultVal;
    }

    public static Boolean toBooleanOrThrow(String str) {
        if (str == null || str.isBlank()) {
            throw new TypeCastException(str, Boolean.class);
        }
        String normalized = str.trim().toLowerCase();
        if (TRUE_VALUES.contains(normalized)) {
            return Boolean.TRUE;
        }
        if (FALSE_VALUES.contains(normalized)) {
            return Boolean.FALSE;
        }
        throw new TypeCastException(str, Boolean.class);
    }

    // --- Enum ---

    public static <E extends Enum<E>> E toEnum(Class<E> enumClass, String str, E defaultVal) {
        if (enumClass == null || str == null || str.isBlank()) {
            return defaultVal;
        }
        String normalized = str.trim();
        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(normalized) ||
                constant.name().replace("_", "").equalsIgnoreCase(normalized.replace("-", "").replace("_", ""))) {
                return constant;
            }
        }
        return defaultVal;
    }

    public static <E extends Enum<E>> E toEnumOrThrow(Class<E> enumClass, String str) {
        Objects.requireNonNull(enumClass, "enumClass must not be null");
        E result = toEnum(enumClass, str, null);
        if (result == null) {
            throw new TypeCastException(str, enumClass);
        }
        return result;
    }

    // --- LocalDate ---

    public static LocalDate toLocalDate(String str, LocalDate defaultVal) {
        if (str == null || str.isBlank()) {
            return defaultVal;
        }
        String trimmed = str.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return defaultVal;
    }

    public static LocalDate toLocalDateOrThrow(String str) {
        LocalDate result = toLocalDate(str, null);
        if (result == null) {
            throw new TypeCastException(str, LocalDate.class);
        }
        return result;
    }

    // --- LocalDateTime ---

    public static LocalDateTime toLocalDateTime(String str, LocalDateTime defaultVal) {
        if (str == null || str.isBlank()) {
            return defaultVal;
        }
        String trimmed = str.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return defaultVal;
    }

    public static LocalDateTime toLocalDateTimeOrThrow(String str) {
        LocalDateTime result = toLocalDateTime(str, null);
        if (result == null) {
            throw new TypeCastException(str, LocalDateTime.class);
        }
        return result;
    }

    // --- List ---

    public static List<String> toList(String str, String delimiter) {
        if (str == null || str.isBlank()) {
            return List.of();
        }
        String delim = (delimiter == null || delimiter.isEmpty()) ? "," : delimiter;
        String[] tokens = str.split(java.util.regex.Pattern.quote(delim));
        List<String> list = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public static <T> List<T> toList(String str, String delimiter, Function<String, T> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        List<String> rawTokens = toList(str, delimiter);
        List<T> list = new ArrayList<>(rawTokens.size());
        for (String raw : rawTokens) {
            list.add(mapper.apply(raw));
        }
        return Collections.unmodifiableList(list);
    }

    // --- Safe Result Pattern ---

    public static <T> ConversionResult<T> tryConvert(String str, Function<String, T> parser) {
        if (str == null || str.isBlank()) {
            return ConversionResult.failure(new IllegalArgumentException("Input string is null or blank"));
        }
        try {
            T value = parser.apply(str);
            return ConversionResult.success(value);
        } catch (Throwable t) {
            return ConversionResult.failure(t);
        }
    }
}
