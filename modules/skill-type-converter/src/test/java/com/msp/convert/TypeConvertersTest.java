package com.msp.convert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypeConverters Test")
class TypeConvertersTest {

    enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    @Test
    @DisplayName("Integer conversions with fallback")
    void testToInteger() {
        assertEquals(42, TypeConverters.toInteger("42", 0));
        assertEquals(-10, TypeConverters.toInteger("-10", 0));
        assertEquals(99, TypeConverters.toInteger("invalid", 99));
        assertEquals(99, TypeConverters.toInteger(null, 99));
        assertEquals(99, TypeConverters.toInteger("", 99));

        assertEquals(10, TypeConverters.toInt("10", 0));
        assertEquals(5, TypeConverters.toInt("xyz", 5));

        assertEquals(123, TypeConverters.toIntegerOrThrow("123"));
        assertThrows(TypeCastException.class, () -> TypeConverters.toIntegerOrThrow("not-a-number"));
    }

    @Test
    @DisplayName("Long and Double conversions with fallback")
    void testLongAndDouble() {
        assertEquals(10000000000L, TypeConverters.toLong("10000000000", 0L));
        assertEquals(0L, TypeConverters.toLong("abc", 0L));

        assertEquals(3.14159, TypeConverters.toDouble("3.14159", 0.0), 0.0001);
        assertEquals(1.5, TypeConverters.toDouble("bad", 1.5), 0.0001);

        assertEquals(100L, TypeConverters.toLongOrThrow("100"));
        assertEquals(2.5, TypeConverters.toDoubleOrThrow("2.5"), 0.0001);
    }

    @ParameterizedTest
    @CsvSource({
            "true, true",
            "TRUE, true",
            "1, true",
            "yes, true",
            "Y, true",
            "on, true",
            "false, false",
            "0, false",
            "no, false",
            "n, false",
            "off, false"
    })
    @DisplayName("Boolean conversion for various representations")
    void testToBoolean(String input, boolean expected) {
        assertEquals(expected, TypeConverters.toBoolean(input, !expected));
        assertEquals(expected, TypeConverters.toBooleanOrThrow(input));
    }

    @Test
    @DisplayName("Boolean fallback and failure cases")
    void testBooleanFallback() {
        assertTrue(TypeConverters.toBoolean("random", true));
        assertFalse(TypeConverters.toBoolean(null, false));
        assertThrows(TypeCastException.class, () -> TypeConverters.toBooleanOrThrow("unknown"));
    }

    @Test
    @DisplayName("Enum conversion with case-insensitivity and hyphen matching")
    void testToEnum() {
        assertEquals(Priority.HIGH, TypeConverters.toEnum(Priority.class, "HIGH", Priority.LOW));
        assertEquals(Priority.HIGH, TypeConverters.toEnum(Priority.class, "high", Priority.LOW));
        assertEquals(Priority.CRITICAL, TypeConverters.toEnum(Priority.class, "critical", Priority.LOW));
        assertEquals(Priority.MEDIUM, TypeConverters.toEnum(Priority.class, "non_existent", Priority.MEDIUM));

        assertEquals(Priority.HIGH, TypeConverters.toEnumOrThrow(Priority.class, "HIGH"));
        assertThrows(TypeCastException.class, () -> TypeConverters.toEnumOrThrow(Priority.class, "NOT_FOUND"));
    }

    @Test
    @DisplayName("LocalDate multi-format conversion")
    void testToLocalDate() {
        LocalDate expected = LocalDate.of(2026, 10, 30);

        assertEquals(expected, TypeConverters.toLocalDate("2026-10-30", null));
        assertEquals(expected, TypeConverters.toLocalDate("20261030", null));
        assertEquals(expected, TypeConverters.toLocalDate("2026/10/30", null));
        assertEquals(expected, TypeConverters.toLocalDate("2026.10.30", null));
        assertEquals(expected, TypeConverters.toLocalDate("30-10-2026", null));

        LocalDate fallback = LocalDate.of(2000, 1, 1);
        assertEquals(fallback, TypeConverters.toLocalDate("not-a-date", fallback));
        assertEquals(expected, TypeConverters.toLocalDateOrThrow("2026-10-30"));
        assertThrows(TypeCastException.class, () -> TypeConverters.toLocalDateOrThrow("invalid"));
    }

    @Test
    @DisplayName("LocalDateTime multi-format conversion")
    void testToLocalDateTime() {
        LocalDateTime expected = LocalDateTime.of(2026, 10, 30, 15, 30, 0);

        assertEquals(expected, TypeConverters.toLocalDateTime("2026-10-30T15:30:00", null));
        assertEquals(expected, TypeConverters.toLocalDateTime("2026-10-30 15:30:00", null));
        assertEquals(expected, TypeConverters.toLocalDateTime("2026/10/30 15:30:00", null));

        LocalDateTime fallback = LocalDateTime.of(2000, 1, 1, 0, 0);
        assertEquals(fallback, TypeConverters.toLocalDateTime("invalid-time", fallback));
        assertEquals(expected, TypeConverters.toLocalDateTimeOrThrow("2026-10-30 15:30:00"));
        assertThrows(TypeCastException.class, () -> TypeConverters.toLocalDateTimeOrThrow("bad-dt"));
    }

    @Test
    @DisplayName("List parsing and mapping")
    void testToList() {
        List<String> items = TypeConverters.toList("apple, banana, orange", ",");
        assertEquals(List.of("apple", "banana", "orange"), items);

        List<String> semiItems = TypeConverters.toList("one; two; ; three", ";");
        assertEquals(List.of("one", "two", "three"), semiItems);

        List<Integer> numbers = TypeConverters.toList("1, 2, 3, 4", ",", Integer::parseInt);
        assertEquals(List.of(1, 2, 3, 4), numbers);

        assertTrue(TypeConverters.toList("", ",").isEmpty());
        assertTrue(TypeConverters.toList(null, ",").isEmpty());
    }

    @Test
    @DisplayName("ConversionResult functional wrapper")
    void testConversionResult() {
        ConversionResult<Integer> success = TypeConverters.tryConvert("123", Integer::parseInt);
        assertTrue(success.isSuccess());
        assertFalse(success.isFailure());
        assertEquals(123, success.orElse(0));
        assertEquals(123, success.orElseGet(() -> 0));
        assertEquals(123, success.orElseThrow());
        assertTrue(success.toOptional().isPresent());

        ConversionResult<Integer> failure = TypeConverters.tryConvert("abc", Integer::parseInt);
        assertFalse(failure.isSuccess());
        assertTrue(failure.isFailure());
        assertEquals(999, failure.orElse(999));
        assertTrue(failure.toOptional().isEmpty());
        assertThrows(NumberFormatException.class, failure::orElseThrow);
    }
}
