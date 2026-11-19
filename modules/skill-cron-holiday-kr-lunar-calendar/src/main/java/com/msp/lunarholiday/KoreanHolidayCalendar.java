package com.msp.lunarholiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Korean Public Holiday calendar including solar fixed holidays, astronomical lunar holidays,
 * substitute holidays (대체공휴일), and cron schedule adjustment logic.
 */
public class KoreanHolidayCalendar {

    private static final KoreanHolidayCalendar INSTANCE = new KoreanHolidayCalendar();

    private final Map<Integer, List<KoreanHoliday>> yearCache = new ConcurrentHashMap<>();
    private final Map<Integer, Map<LocalDate, KoreanHoliday>> dateMapCache = new ConcurrentHashMap<>();

    public static KoreanHolidayCalendar getInstance() {
        return INSTANCE;
    }

    /**
     * Retrieves all official Korean public holidays (including substitute holidays) for the year.
     */
    public List<KoreanHoliday> getHolidaysForYear(int year) {
        return yearCache.computeIfAbsent(year, this::computeHolidays);
    }

    /**
     * Checks if a given date is an official Korean public holiday.
     */
    public boolean isHoliday(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return getHoliday(date).isPresent();
    }

    /**
     * Retrieves holiday metadata if the date is an official Korean holiday.
     */
    public Optional<KoreanHoliday> getHoliday(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        Map<LocalDate, KoreanHoliday> map = dateMapCache.computeIfAbsent(date.getYear(), y -> {
            Map<LocalDate, KoreanHoliday> m = new HashMap<>();
            for (KoreanHoliday h : getHolidaysForYear(y)) {
                m.put(h.date(), h);
            }
            return m;
        });
        return Optional.ofNullable(map.get(date));
    }

    /**
     * Checks if a date falls on a weekend (Saturday or Sunday).
     */
    public boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    /**
     * Checks if a date is a business day (neither weekend nor public holiday).
     */
    public boolean isBusinessDay(LocalDate date) {
        return !isWeekend(date) && !isHoliday(date);
    }

    /**
     * Adjusts a target execution date according to the given AdjustmentPolicy.
     *
     * @return adjusted business day, or null if policy is SKIP and date is a holiday/weekend
     */
    public LocalDate adjust(LocalDate date, AdjustmentPolicy policy) {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        if (isBusinessDay(date)) {
            return date;
        }

        return switch (policy) {
            case ROLL_FORWARD -> {
                LocalDate cur = date.plusDays(1);
                while (!isBusinessDay(cur)) {
                    cur = cur.plusDays(1);
                }
                yield cur;
            }
            case ROLL_BACKWARD -> {
                LocalDate cur = date.minusDays(1);
                while (!isBusinessDay(cur)) {
                    cur = cur.minusDays(1);
                }
                yield cur;
            }
            case SKIP -> null;
        };
    }

    /**
     * Computes the next N business days starting strictly after the given date.
     */
    public List<LocalDate> nextBusinessDays(LocalDate from, int count) {
        Objects.requireNonNull(from, "from must not be null");
        if (count <= 0) {
            return Collections.emptyList();
        }
        List<LocalDate> list = new ArrayList<>(count);
        LocalDate cur = from.plusDays(1);
        while (list.size() < count) {
            if (isBusinessDay(cur)) {
                list.add(cur);
            }
            cur = cur.plusDays(1);
        }
        return list;
    }

    private List<KoreanHoliday> computeHolidays(int year) {
        List<KoreanHoliday> holidays = new ArrayList<>();
        Set<LocalDate> holidayDates = new HashSet<>();

        // 1. Fixed Solar Holidays
        addHoliday(holidays, holidayDates, LocalDate.of(year, 1, 1), "신정", HolidayType.SOLAR_FIXED);
        addHoliday(holidays, holidayDates, LocalDate.of(year, 3, 1), "삼일절", HolidayType.SOLAR_FIXED);
        addHoliday(holidays, holidayDates, LocalDate.of(year, 5, 5), "어린이날", HolidayType.SOLAR_FIXED);
        addHoliday(holidays, holidayDates, LocalDate.of(year, 6, 6), "현충일", HolidayType.SOLAR_FIXED);
        addHoliday(holidays, holidayDates, LocalDate.of(year, 8, 15), "광복절", HolidayType.SOLAR_FIXED);
        addHoliday(holidays, holidayDates, LocalDate.of(year, 10, 3), "개천절", HolidayType.SOLAR_FIXED);
        addHoliday(holidays, holidayDates, LocalDate.of(year, 10, 9), "한글날", HolidayType.SOLAR_FIXED);
        addHoliday(holidays, holidayDates, LocalDate.of(year, 12, 25), "성탄절", HolidayType.SOLAR_FIXED);

        // 2. Lunar Holidays
        if (KoreanLunarCalendar.isSupported(year)) {
            LocalDate seollal = KoreanLunarCalendar.getSeollal(year);
            addHoliday(holidays, holidayDates, seollal.minusDays(1), "설날 전날", HolidayType.SEOLLAL);
            addHoliday(holidays, holidayDates, seollal, "설날", HolidayType.SEOLLAL);
            addHoliday(holidays, holidayDates, seollal.plusDays(1), "설날 다음날", HolidayType.SEOLLAL);

            LocalDate buddha = KoreanLunarCalendar.getBuddhasBirthday(year);
            addHoliday(holidays, holidayDates, buddha, "부처님오신날", HolidayType.BUDDHAS_BIRTHDAY);

            LocalDate chuseok = KoreanLunarCalendar.getChuseok(year);
            addHoliday(holidays, holidayDates, chuseok.minusDays(1), "추석 전날", HolidayType.CHUSEOK);
            addHoliday(holidays, holidayDates, chuseok, "추석", HolidayType.CHUSEOK);
            addHoliday(holidays, holidayDates, chuseok.plusDays(1), "추석 다음날", HolidayType.CHUSEOK);

            // 3. Substitution Holidays (대체공휴일 규정)
            computeSubstitutes(year, holidays, holidayDates, seollal, buddha, chuseok);
        }

        Collections.sort(holidays);
        return Collections.unmodifiableList(holidays);
    }

    private void computeSubstitutes(
            int year,
            List<KoreanHoliday> holidays,
            Set<LocalDate> holidayDates,
            LocalDate seollal,
            LocalDate buddha,
            LocalDate chuseok
    ) {
        // A. 설날 연휴 (3일): overlaps with Sunday or another holiday -> next non-holiday weekday
        checkLunarHolidaySubstitution(holidays, holidayDates, seollal, "설날");

        // B. 추석 연휴 (3일): overlaps with Sunday or another holiday -> next non-holiday weekday
        checkLunarHolidaySubstitution(holidays, holidayDates, chuseok, "추석");

        // C. 어린이날 (5/5): overlaps with Saturday, Sunday, or another holiday -> next non-holiday weekday
        LocalDate childrenDay = LocalDate.of(year, 5, 5);
        if (isWeekend(childrenDay) || countOverlaps(holidayDates, childrenDay) > 1) {
            addSubstitute(holidays, holidayDates, childrenDay, "어린이날");
        }

        // D. 3/1, 8/15, 10/3, 10/9, 부처님오신날, 성탄절 (12/25): overlaps with Saturday or Sunday
        List<Map.Entry<LocalDate, String>> nationalHolidays = List.of(
                Map.entry(LocalDate.of(year, 3, 1), "삼일절"),
                Map.entry(LocalDate.of(year, 8, 15), "광복절"),
                Map.entry(LocalDate.of(year, 10, 3), "개천절"),
                Map.entry(LocalDate.of(year, 10, 9), "한글날"),
                Map.entry(buddha, "부처님오신날"),
                Map.entry(LocalDate.of(year, 12, 25), "성탄절")
        );

        for (var entry : nationalHolidays) {
            LocalDate d = entry.getKey();
            if (isWeekend(d)) {
                addSubstitute(holidays, holidayDates, d, entry.getValue());
            }
        }
    }

    private void checkLunarHolidaySubstitution(
            List<KoreanHoliday> holidays,
            Set<LocalDate> holidayDates,
            LocalDate centerDay,
            String holidayName
    ) {
        LocalDate[] triplet = new LocalDate[] {
                centerDay.minusDays(1), centerDay, centerDay.plusDays(1)
        };
        boolean needsSub = false;
        for (LocalDate day : triplet) {
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                needsSub = true;
                break;
            }
        }
        if (needsSub) {
            addSubstitute(holidays, holidayDates, triplet[2], holidayName);
        }
    }

    private void addSubstitute(
            List<KoreanHoliday> holidays,
            Set<LocalDate> holidayDates,
            LocalDate triggerDate,
            String originalName
    ) {
        LocalDate candidate = triggerDate.plusDays(1);
        while (isWeekend(candidate) || holidayDates.contains(candidate)) {
            candidate = candidate.plusDays(1);
        }
        addHoliday(holidays, holidayDates, candidate, "대체공휴일(" + originalName + ")", HolidayType.SUBSTITUTE);
    }

    private void addHoliday(List<KoreanHoliday> list, Set<LocalDate> dateSet, LocalDate date, String name, HolidayType type) {
        list.add(new KoreanHoliday(date, name, type, type == HolidayType.SUBSTITUTE));
        dateSet.add(date);
    }

    private int countOverlaps(Set<LocalDate> dates, LocalDate target) {
        int count = 0;
        for (LocalDate d : dates) {
            if (d.equals(target)) count++;
        }
        return count;
    }
}
