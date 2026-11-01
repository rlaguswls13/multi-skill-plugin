package com.msp.etag;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Optional;

/**
 * Robust HTTP-date parser conforming to RFC 7231 Section 7.1.1.1.
 *
 * <p>Supports:
 * <ul>
 *   <li>RFC 1123 / RFC 822: {@code Sun, 06 Nov 1994 08:49:37 GMT} (preferred)</li>
 *   <li>RFC 850: {@code Sunday, 06-Nov-94 08:49:37 GMT} (obsolete)</li>
 *   <li>ANSI C asctime(): {@code Sun Nov  6 08:49:37 1994} (obsolete)</li>
 * </ul>
 */
public final class HttpDateParser {

    /** Preferred RFC 1123 / RFC 7231 parser (allows 1 or 2-digit days) */
    public static final DateTimeFormatter RFC_1123_PARSER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE, ")
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendPattern(" MMM yyyy HH:mm:ss ")
            .appendZoneOrOffsetId()
            .toFormatter(Locale.US);

    /** RFC 850 formatter (2-digit year) */
    private static final DateTimeFormatter RFC_850_PARSER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEEE, dd-MMM-")
            .appendValueReduced(ChronoField.YEAR_OF_ERA, 2, 2, java.time.LocalDate.now().getYear() - 50)
            .appendPattern(" HH:mm:ss zzz")
            .toFormatter(Locale.US);

    /** ANSI C asctime() format: "Sun Nov  6 08:49:37 1994" or "Sun Nov 06 08:49:37 1994" */
    private static final DateTimeFormatter ASCTIME_PARSER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE MMM ")
            .padNext(2, ' ')
            .appendPattern("d HH:mm:ss yyyy")
            .toFormatter(Locale.US);

    /** Standard RFC 7231 output formatter (2-digit day, GMT) */
    private static final DateTimeFormatter RFC_1123_OUTPUT = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneId.of("GMT"));

    private HttpDateParser() {
    }

    /**
     * Parses an HTTP date string into an {@link Instant}.
     *
     * @param dateString raw date string
     * @return Optional containing the parsed Instant, or empty if invalid
     */
    public static Optional<Instant> parse(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return Optional.empty();
        }

        String trimmed = dateString.trim();

        // 1. Try RFC 1123
        try {
            return Optional.of(ZonedDateTime.parse(trimmed, RFC_1123_PARSER).toInstant());
        } catch (DateTimeParseException ignored) {
        }

        // 2. Try RFC 850
        try {
            return Optional.of(ZonedDateTime.parse(trimmed, RFC_850_PARSER).toInstant());
        } catch (DateTimeParseException ignored) {
        }

        // 3. Try asctime
        try {
            LocalDateTime ldt = LocalDateTime.parse(trimmed, ASCTIME_PARSER);
            return Optional.of(ldt.toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
        }

        return Optional.empty();
    }

    /**
     * Formats an {@link Instant} as an RFC 1123 HTTP-date string in GMT.
     */
    public static String formatRfc1123(Instant instant) {
        if (instant == null) {
            return null;
        }
        return RFC_1123_OUTPUT.format(instant);
    }
}
