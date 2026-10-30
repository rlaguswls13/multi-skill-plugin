package com.msp.starter.datetime;

import com.msp.bizday.BusinessDayCalculator;
import com.msp.crontext.CronHumanReadable;
import com.msp.dday.DDayCalculator;
import com.msp.dday.DDayResult;
import com.msp.holiday.KoreanHolidayCalculator;
import com.msp.solarlunar.LunarDate;
import com.msp.solarlunar.SolarLunarConverter;
import com.msp.timeago.TimeAgoFormatter;
import com.msp.timeparse.Iso8601TimeParser;
import com.msp.tzconvert.TimezoneConverter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 한국 공휴일, 영업일, 상대시간, 일시 파싱, 타임존, Cron 설명, D-Day, 양음력 변환 종합 스타터 파사드.
 */
public final class DateTimeKit {

    private DateTimeKit() {}

    public static boolean isKoreanHoliday(LocalDate date) {
        return KoreanHolidayCalculator.getInstance().isHoliday(date);
    }

    public static BusinessDayCalculator createKoreanBusinessDayCalculator() {
        return BusinessDayCalculator.of(KoreanHolidayCalculator.getInstance()::isHoliday);
    }

    public static String formatTimeAgo(Instant time, Locale locale) {
        return TimeAgoFormatter.standard().format(time, locale);
    }

    public static Optional<Instant> parseIsoTime(String text) {
        return Iso8601TimeParser.parseToInstant(text);
    }

    public static ZonedDateTime convertTimezone(ZonedDateTime source, String targetZoneAlias) {
        return TimezoneConverter.convert(source, targetZoneAlias);
    }

    public static String describeCron(String cronExpression) {
        return CronHumanReadable.describe(cronExpression);
    }

    public static DDayResult calculateDDay(LocalDate target, LocalDate base) {
        return DDayCalculator.calculate(target, base);
    }

    public static LunarDate toLunarDate(LocalDate solarDate) {
        return SolarLunarConverter.toLunar(solarDate);
    }
}
