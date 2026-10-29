package com.msp.tzconvert;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Timezone converter and offset analyzer supporting standard aliases (KST, JST, PST, EST, etc.).
 */
public final class TimezoneConverter {

    private static final Map<String, String> ALIAS_MAP = new HashMap<>();

    static {
        // Universal UTC / GMT
        ALIAS_MAP.put("UTC", "UTC");
        ALIAS_MAP.put("GMT", "GMT");
        ALIAS_MAP.put("Z", "UTC");

        // Asia
        ALIAS_MAP.put("KST", "Asia/Seoul");
        ALIAS_MAP.put("JST", "Asia/Tokyo");
        ALIAS_MAP.put("SGT", "Asia/Singapore");
        ALIAS_MAP.put("HKT", "Asia/Hong_Kong");
        ALIAS_MAP.put("CST_CHINA", "Asia/Shanghai");
        ALIAS_MAP.put("IST", "Asia/Kolkata");

        // North America
        ALIAS_MAP.put("EST", "America/New_York");
        ALIAS_MAP.put("EDT", "America/New_York");
        ALIAS_MAP.put("CST", "America/Chicago");
        ALIAS_MAP.put("CDT", "America/Chicago");
        ALIAS_MAP.put("MST", "America/Denver");
        ALIAS_MAP.put("MDT", "America/Denver");
        ALIAS_MAP.put("PST", "America/Los_Angeles");
        ALIAS_MAP.put("PDT", "America/Los_Angeles");
        ALIAS_MAP.put("AKST", "America/Anchorage");
        ALIAS_MAP.put("HST", "Pacific/Honolulu");

        // Europe
        ALIAS_MAP.put("WET", "Europe/London");
        ALIAS_MAP.put("BST", "Europe/London");
        ALIAS_MAP.put("CET", "Europe/Paris");
        ALIAS_MAP.put("CEST", "Europe/Paris");
        ALIAS_MAP.put("EET", "Europe/Athens");
        ALIAS_MAP.put("EEST", "Europe/Athens");

        // Oceania
        ALIAS_MAP.put("AEST", "Australia/Sydney");
        ALIAS_MAP.put("AEDT", "Australia/Sydney");
        ALIAS_MAP.put("NZST", "Pacific/Auckland");
    }

    private TimezoneConverter() {
    }

    /**
     * Resolves a timezone ID or common abbreviation (e.g. "KST", "PST", "UTC", "Asia/Seoul").
     *
     * @param zoneOrAlias zone identifier or known alias
     * @return resolved {@link ZoneId}
     * @throws DateTimeException if zone cannot be resolved
     */
    public static ZoneId resolveZone(String zoneOrAlias) {
        Objects.requireNonNull(zoneOrAlias, "zoneOrAlias must not be null");
        String trimmed = zoneOrAlias.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);

        if (ALIAS_MAP.containsKey(upper)) {
            return ZoneId.of(ALIAS_MAP.get(upper));
        }

        // Try standard ZoneId (e.g. "Asia/Seoul", "+09:00", "America/New_York")
        return ZoneId.of(trimmed, ZoneId.SHORT_IDS);
    }

    /**
     * Converts a {@link ZonedDateTime} to the target {@link ZoneId}.
     *
     * @param dateTime   source zoned date time
     * @param targetZone destination zone
     * @return converted ZonedDateTime
     */
    public static ZonedDateTime convert(ZonedDateTime dateTime, ZoneId targetZone) {
        Objects.requireNonNull(dateTime, "dateTime must not be null");
        Objects.requireNonNull(targetZone, "targetZone must not be null");
        return dateTime.withZoneSameInstant(targetZone);
    }

    /**
     * Converts a {@link ZonedDateTime} to the target zone specified by name or alias.
     */
    public static ZonedDateTime convert(ZonedDateTime dateTime, String targetZoneOrAlias) {
        return convert(dateTime, resolveZone(targetZoneOrAlias));
    }

    /**
     * Converts an {@link Instant} to a {@link ZonedDateTime} in the target {@link ZoneId}.
     *
     * @param instant    instant in time
     * @param targetZone destination zone
     * @return ZonedDateTime in target zone
     */
    public static ZonedDateTime convert(Instant instant, ZoneId targetZone) {
        Objects.requireNonNull(instant, "instant must not be null");
        Objects.requireNonNull(targetZone, "targetZone must not be null");
        return instant.atZone(targetZone);
    }

    /**
     * Converts an {@link Instant} to a {@link ZonedDateTime} in the target zone specified by name or alias.
     */
    public static ZonedDateTime convert(Instant instant, String targetZoneOrAlias) {
        return convert(instant, resolveZone(targetZoneOrAlias));
    }

    /**
     * Calculates the offset difference (target zone2 relative to source zone1) at the given instant.
     * <p>
     * For instance, if zone1 is UTC (+00:00) and zone2 is KST (+09:00), the returned duration is +9 hours.
     *
     * @param zone1   source zone
     * @param zone2   target zone
     * @param instant instant at which to check offsets (accounting for DST transitions)
     * @return duration difference: {@code offset(zone2) - offset(zone1)}
     */
    public static Duration getOffsetDifference(ZoneId zone1, ZoneId zone2, Instant instant) {
        Objects.requireNonNull(zone1, "zone1 must not be null");
        Objects.requireNonNull(zone2, "zone2 must not be null");
        Objects.requireNonNull(instant, "instant must not be null");

        ZoneOffset offset1 = zone1.getRules().getOffset(instant);
        ZoneOffset offset2 = zone2.getRules().getOffset(instant);
        long diffSeconds = (long) offset2.getTotalSeconds() - offset1.getTotalSeconds();
        return Duration.ofSeconds(diffSeconds);
    }

    /**
     * Calculates the offset difference between two zones specified by name or alias.
     */
    public static Duration getOffsetDifference(String zone1, String zone2, Instant instant) {
        return getOffsetDifference(resolveZone(zone1), resolveZone(zone2), instant);
    }

    /**
     * Checks if the given {@link ZonedDateTime} is currently observing Daylight Saving Time (DST).
     *
     * @param dateTime zoned date time
     * @return {@code true} if in DST, {@code false} otherwise
     */
    public static boolean isDaylightSavingTime(ZonedDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime must not be null");
        return dateTime.getZone().getRules().isDaylightSavings(dateTime.toInstant());
    }

    /**
     * Checks if daylight saving time is active in the given zone at the specified instant.
     */
    public static boolean isDaylightSavingTime(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        return zone.getRules().isDaylightSavings(instant);
    }

    /**
     * Returns the exact {@link ZoneOffset} for the given zone at the specified instant.
     */
    public static ZoneOffset getOffset(ZoneId zone, Instant instant) {
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(instant, "instant must not be null");
        return zone.getRules().getOffset(instant);
    }

    /**
     * Formats a ZonedDateTime into an ISO-8601 offset string.
     */
    public static String formatIso(ZonedDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime must not be null");
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
    }

    /**
     * Formats a ZonedDateTime with a custom pattern.
     */
    public static String format(ZonedDateTime dateTime, String pattern) {
        Objects.requireNonNull(dateTime, "dateTime must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        return DateTimeFormatter.ofPattern(pattern).format(dateTime);
    }
}
