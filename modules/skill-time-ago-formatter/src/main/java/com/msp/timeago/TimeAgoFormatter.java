package com.msp.timeago;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Human-friendly relative time formatter supporting Korean and English locales
 * with configurable thresholds, pluggable Clock, and fallback absolute formatting.
 */
public final class TimeAgoFormatter {

    private static final TimeAgoFormatter DEFAULT_INSTANCE = new TimeAgoFormatter(
            Clock.systemUTC(),
            ZoneId.systemDefault(),
            Locale.KOREAN,
            30, // fallback to absolute after 30 days
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            5   // just now threshold in seconds
    );

    private final Clock clock;
    private final ZoneId zoneId;
    private final Locale defaultLocale;
    private final Integer maxRelativeDays; // if null, always format relatively
    private final DateTimeFormatter fallbackFormatter;
    private final int justNowThresholdSeconds;

    public TimeAgoFormatter(
            Clock clock,
            ZoneId zoneId,
            Locale defaultLocale,
            Integer maxRelativeDays,
            DateTimeFormatter fallbackFormatter,
            int justNowThresholdSeconds
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale must not be null");
        this.maxRelativeDays = maxRelativeDays;
        this.fallbackFormatter = Objects.requireNonNull(fallbackFormatter, "fallbackFormatter must not be null");
        this.justNowThresholdSeconds = justNowThresholdSeconds;
    }

    /**
     * Returns standard singleton instance (Korean default, fallback after 30 days to yyyy-MM-dd).
     */
    public static TimeAgoFormatter standard() {
        return DEFAULT_INSTANCE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Formats an Instant relative to current clock time with default locale.
     *
     * @param instant target instant
     * @return formatted relative time string
     */
    public String format(Instant instant) {
        return format(instant, defaultLocale);
    }

    /**
     * Formats an Instant relative to current clock time with the specified locale.
     *
     * @param instant target instant
     * @param locale  locale (e.g. Locale.KOREAN, Locale.ENGLISH)
     * @return formatted relative time string
     */
    public String format(Instant instant, Locale locale) {
        return format(instant, clock.instant(), locale);
    }

    /**
     * Formats an Instant relative to a specified reference Instant and Locale.
     *
     * @param target target instant
     * @param now    reference instant
     * @param locale locale
     * @return formatted relative time string
     */
    public String format(Instant target, Instant now, Locale locale) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Locale activeLocale = (locale != null) ? locale : defaultLocale;
        boolean isKorean = isKoreanLocale(activeLocale);

        Duration duration = Duration.between(target, now);
        boolean isPast = !duration.isNegative();
        long diffSeconds = Math.abs(duration.toSeconds());

        // 1. "Just now" / "방금 전"
        if (diffSeconds < justNowThresholdSeconds) {
            if (isKorean) {
                return isPast ? "방금 전" : "곧";
            } else {
                return isPast ? "just now" : "in a moment";
            }
        }

        // 2. Seconds (< 60s)
        if (diffSeconds < 60) {
            if (isKorean) {
                return isPast ? diffSeconds + "초 전" : diffSeconds + "초 후";
            } else {
                String unit = diffSeconds == 1 ? "second" : "seconds";
                return isPast ? diffSeconds + " " + unit + " ago" : "in " + diffSeconds + " " + unit;
            }
        }

        // 3. Minutes (< 60m)
        long diffMinutes = diffSeconds / 60;
        if (diffMinutes < 60) {
            if (isKorean) {
                return isPast ? diffMinutes + "분 전" : diffMinutes + "분 후";
            } else {
                String unit = diffMinutes == 1 ? "minute" : "minutes";
                return isPast ? diffMinutes + " " + unit + " ago" : "in " + diffMinutes + " " + unit;
            }
        }

        // 4. Hours (< 24h)
        long diffHours = diffMinutes / 60;
        if (diffHours < 24) {
            if (isKorean) {
                return isPast ? diffHours + "시간 전" : diffHours + "시간 후";
            } else {
                String unit = diffHours == 1 ? "hour" : "hours";
                return isPast ? diffHours + " " + unit + " ago" : "in " + diffHours + " " + unit;
            }
        }

        // 5. Days
        long diffDays = diffHours / 24;

        // Check fallback threshold (e.g. > 30 days)
        if (maxRelativeDays != null && diffDays >= maxRelativeDays) {
            return fallbackFormatter.format(target.atZone(zoneId));
        }

        if (diffDays == 1) {
            if (isKorean) {
                return isPast ? "어제" : "내일";
            } else {
                return isPast ? "yesterday" : "tomorrow";
            }
        }

        if (diffDays < 30) {
            if (isKorean) {
                return isPast ? diffDays + "일 전" : diffDays + "일 후";
            } else {
                String unit = diffDays == 1 ? "day" : "days";
                return isPast ? diffDays + " " + unit + " ago" : "in " + diffDays + " " + unit;
            }
        }

        // 6. Months (< 365d)
        long diffMonths = diffDays / 30;
        if (diffMonths < 12) {
            if (isKorean) {
                return isPast ? diffMonths + "개월 전" : diffMonths + "개월 후";
            } else {
                String unit = diffMonths == 1 ? "month" : "months";
                return isPast ? diffMonths + " " + unit + " ago" : "in " + diffMonths + " " + unit;
            }
        }

        // 7. Years
        long diffYears = diffDays / 365;
        if (isKorean) {
            return isPast ? diffYears + "년 전" : diffYears + "년 후";
        } else {
            String unit = diffYears == 1 ? "year" : "years";
            return isPast ? diffYears + " " + unit + " ago" : "in " + diffYears + " " + unit;
        }
    }

    /**
     * Formats a ZonedDateTime relative to now.
     */
    public String format(ZonedDateTime dateTime) {
        return format(dateTime.toInstant(), defaultLocale);
    }

    /**
     * Formats a ZonedDateTime relative to now with the specified Locale.
     */
    public String format(ZonedDateTime dateTime, Locale locale) {
        return format(dateTime.toInstant(), locale);
    }

    private static boolean isKoreanLocale(Locale locale) {
        return "ko".equalsIgnoreCase(locale.getLanguage())
                || Locale.KOREAN.equals(locale)
                || Locale.KOREA.equals(locale);
    }

    public static final class Builder {
        private Clock clock = Clock.systemUTC();
        private ZoneId zoneId = ZoneId.systemDefault();
        private Locale defaultLocale = Locale.KOREAN;
        private Integer maxRelativeDays = 30;
        private DateTimeFormatter fallbackFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        private int justNowThresholdSeconds = 5;

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            return this;
        }

        public Builder zoneId(ZoneId zoneId) {
            this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
            return this;
        }

        public Builder defaultLocale(Locale defaultLocale) {
            this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale must not be null");
            return this;
        }

        public Builder maxRelativeDays(Integer maxRelativeDays) {
            this.maxRelativeDays = maxRelativeDays;
            return this;
        }

        public Builder fallbackFormatter(DateTimeFormatter fallbackFormatter) {
            this.fallbackFormatter = Objects.requireNonNull(fallbackFormatter, "fallbackFormatter must not be null");
            return this;
        }

        public Builder justNowThresholdSeconds(int justNowThresholdSeconds) {
            this.justNowThresholdSeconds = justNowThresholdSeconds;
            return this;
        }

        public TimeAgoFormatter build() {
            return new TimeAgoFormatter(clock, zoneId, defaultLocale, maxRelativeDays, fallbackFormatter, justNowThresholdSeconds);
        }
    }
}
