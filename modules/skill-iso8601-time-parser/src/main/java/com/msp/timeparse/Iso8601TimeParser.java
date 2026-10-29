package com.msp.timeparse;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resilient multi-format parser for ISO-8601, RFC-1123, standard date-time, and epoch timestamp strings.
 */
public final class Iso8601TimeParser {

    private static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;
    private static final Pattern ALL_DIGITS = Pattern.compile("^\\d+$");

    // Standard date-time formatters with optional fractional seconds
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).toFormatter(),
            new DateTimeFormatterBuilder().appendPattern("yyyy/MM/dd HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).toFormatter(),
            new DateTimeFormatterBuilder().appendPattern("yyyy.MM.dd HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).toFormatter(),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    );

    // Date-only formatters
    private static final List<DateTimeFormatter> DATE_ONLY_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    private Iso8601TimeParser() {
    }

    /**
     * Parses the string into an {@link Instant} using UTC as default timezone for local formats.
     *
     * @param input date-time string
     * @return Optional of Instant, or empty if parsing fails
     */
    public static Optional<Instant> parseToInstant(String input) {
        return parseToInstant(input, DEFAULT_ZONE);
    }

    /**
     * Parses the string into an {@link Instant} using the given default timezone for local formats.
     *
     * @param input       date-time string
     * @param defaultZone timezone applied when input lacks offset/zone
     * @return Optional of Instant, or empty if parsing fails
     */
    public static Optional<Instant> parseToInstant(String input, ZoneId defaultZone) {
        ZoneId zone = (defaultZone != null) ? defaultZone : DEFAULT_ZONE;
        return parseToZonedDateTime(input, zone).map(ZonedDateTime::toInstant);
    }

    /**
     * Parses the string into a {@link ZonedDateTime} using the given default timezone.
     *
     * @param input       date-time string
     * @param defaultZone timezone applied when input lacks offset/zone
     * @return Optional of ZonedDateTime, or empty if parsing fails
     */
    public static Optional<ZonedDateTime> parseToZonedDateTime(String input, ZoneId defaultZone) {
        if (input == null) {
            return Optional.empty();
        }
        String clean = cleanInput(input);
        if (clean.isEmpty()) {
            return Optional.empty();
        }
        ZoneId zone = (defaultZone != null) ? defaultZone : DEFAULT_ZONE;

        // 1. Check if input is epoch timestamp
        Optional<Instant> epochInstant = tryParseEpoch(clean);
        if (epochInstant.isPresent()) {
            return Optional.of(epochInstant.get().atZone(zone));
        }

        // 2. Try date-time formatters
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                TemporalAccessor accessor = formatter.parseBest(clean,
                        ZonedDateTime::from,
                        OffsetDateTime::from,
                        LocalDateTime::from,
                        LocalDate::from
                );

                if (accessor instanceof ZonedDateTime zdt) {
                    return Optional.of(zdt);
                } else if (accessor instanceof OffsetDateTime odt) {
                    return Optional.of(odt.toZonedDateTime());
                } else if (accessor instanceof LocalDateTime ldt) {
                    return Optional.of(ldt.atZone(zone));
                } else if (accessor instanceof LocalDate ld) {
                    return Optional.of(ld.atStartOfDay(zone));
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Try date-only formatters
        for (DateTimeFormatter formatter : DATE_ONLY_FORMATTERS) {
            try {
                LocalDate ld = LocalDate.parse(clean, formatter);
                return Optional.of(ld.atStartOfDay(zone));
            } catch (Exception ignored) {
            }
        }

        return Optional.empty();
    }

    /**
     * Parses the string into a {@link LocalDate}.
     *
     * @param input date-time string
     * @return Optional of LocalDate, or empty if parsing fails
     */
    public static Optional<LocalDate> parseToLocalDate(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String clean = cleanInput(input);
        if (clean.isEmpty()) {
            return Optional.empty();
        }

        // 1. Try date-only formatters first
        for (DateTimeFormatter formatter : DATE_ONLY_FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(clean, formatter));
            } catch (Exception ignored) {
            }
        }

        // 2. Fall back to parsing full date-time and extracting LocalDate
        return parseToZonedDateTime(clean, DEFAULT_ZONE).map(ZonedDateTime::toLocalDate);
    }

    /**
     * Parses the string into a {@link LocalDateTime}.
     *
     * @param input date-time string
     * @return Optional of LocalDateTime, or empty if parsing fails
     */
    public static Optional<LocalDateTime> parseToLocalDateTime(String input) {
        return parseToZonedDateTime(input, DEFAULT_ZONE).map(ZonedDateTime::toLocalDateTime);
    }

    private static String cleanInput(String input) {
        String s = input.trim();
        // Remove surrounding double or single quotes if present
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static Optional<Instant> tryParseEpoch(String clean) {
        if (!ALL_DIGITS.matcher(clean).matches()) {
            return Optional.empty();
        }

        int len = clean.length();
        // 8 digits is yyyyMMdd (e.g. 20261030) and 14 digits is yyyyMMddHHmmss (e.g. 20261030120000)
        if (len == 8 || len == 14) {
            return Optional.empty();
        }

        try {
            long value = Long.parseLong(clean);
            if (len == 10) {
                // Unix epoch seconds
                return Optional.of(Instant.ofEpochSecond(value));
            } else if (len == 13) {
                // Unix epoch milliseconds
                return Optional.of(Instant.ofEpochMilli(value));
            } else if (len == 16) {
                // Microseconds
                return Optional.of(Instant.ofEpochSecond(value / 1_000_000, (value % 1_000_000) * 1_000));
            } else if (len == 19) {
                // Nanoseconds
                return Optional.of(Instant.ofEpochSecond(value / 1_000_000_000, value % 1_000_000_000));
            }
        } catch (NumberFormatException ignored) {
        }
        return Optional.empty();
    }
}
