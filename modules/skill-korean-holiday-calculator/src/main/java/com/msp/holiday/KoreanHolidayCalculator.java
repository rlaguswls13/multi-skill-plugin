package com.msp.holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Korean national and substitute holiday calculator compliant with official Korean regulations
 * ("관공서의 공휴일에 관한 규정" 및 동 시행령 제2조, 제2조의2).
 * <p>
 * Supported features:
 * <ul>
 *   <li>Solar public holidays: 신정 (1/1), 삼일절 (3/1), 어린이날 (5/5), 현충일 (6/6), 광복절 (8/15),
 *       개천절 (10/3), 한글날 (10/9), 성탄절 (12/25)</li>
 *   <li>Lunar public holidays (accurate solar dates for 2018–2040): 설날 3일 연휴, 부처님오신날, 추석 3일 연휴</li>
 *   <li>Substitute holiday calculation: applies when qualifying holidays overlap with weekend or other holidays</li>
 *   <li>Thread-safe caching and custom/temporary holiday extension</li>
 * </ul>
 */
public final class KoreanHolidayCalculator {

    private static final KoreanHolidayCalculator DEFAULT_INSTANCE = new KoreanHolidayCalculator();

    // Solar reference dates for Lunar holidays: year -> [Seollal(음 1/1), Buddha(음 4/8), Chuseok(음 8/15)]
    private static final Map<Integer, LunarHolidaySolarDates> LUNAR_DATA = Map.ofEntries(
            Map.entry(2018, new LunarHolidaySolarDates(LocalDate.of(2018, 2, 16), LocalDate.of(2018, 5, 22), LocalDate.of(2018, 9, 24))),
            Map.entry(2019, new LunarHolidaySolarDates(LocalDate.of(2019, 2, 5), LocalDate.of(2019, 5, 12), LocalDate.of(2019, 9, 13))),
            Map.entry(2020, new LunarHolidaySolarDates(LocalDate.of(2020, 1, 25), LocalDate.of(2020, 4, 30), LocalDate.of(2020, 10, 1))),
            Map.entry(2021, new LunarHolidaySolarDates(LocalDate.of(2021, 2, 12), LocalDate.of(2021, 5, 19), LocalDate.of(2021, 9, 21))),
            Map.entry(2022, new LunarHolidaySolarDates(LocalDate.of(2022, 2, 1), LocalDate.of(2022, 5, 8), LocalDate.of(2022, 9, 10))),
            Map.entry(2023, new LunarHolidaySolarDates(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 5, 27), LocalDate.of(2023, 9, 29))),
            Map.entry(2024, new LunarHolidaySolarDates(LocalDate.of(2024, 2, 10), LocalDate.of(2024, 5, 15), LocalDate.of(2024, 9, 17))),
            Map.entry(2025, new LunarHolidaySolarDates(LocalDate.of(2025, 1, 29), LocalDate.of(2025, 5, 5), LocalDate.of(2025, 10, 6))),
            Map.entry(2026, new LunarHolidaySolarDates(LocalDate.of(2026, 2, 17), LocalDate.of(2026, 5, 24), LocalDate.of(2026, 9, 25))),
            Map.entry(2027, new LunarHolidaySolarDates(LocalDate.of(2027, 2, 7), LocalDate.of(2027, 5, 13), LocalDate.of(2027, 9, 15))),
            Map.entry(2028, new LunarHolidaySolarDates(LocalDate.of(2028, 1, 27), LocalDate.of(2028, 5, 2), LocalDate.of(2028, 10, 3))),
            Map.entry(2029, new LunarHolidaySolarDates(LocalDate.of(2029, 2, 13), LocalDate.of(2029, 5, 20), LocalDate.of(2029, 9, 22))),
            Map.entry(2030, new LunarHolidaySolarDates(LocalDate.of(2030, 2, 3), LocalDate.of(2030, 5, 9), LocalDate.of(2030, 9, 12))),
            Map.entry(2031, new LunarHolidaySolarDates(LocalDate.of(2031, 1, 23), LocalDate.of(2031, 5, 28), LocalDate.of(2031, 10, 1))),
            Map.entry(2032, new LunarHolidaySolarDates(LocalDate.of(2032, 2, 11), LocalDate.of(2032, 5, 16), LocalDate.of(2032, 9, 19))),
            Map.entry(2033, new LunarHolidaySolarDates(LocalDate.of(2033, 1, 31), LocalDate.of(2033, 5, 6), LocalDate.of(2033, 10, 8))),
            Map.entry(2034, new LunarHolidaySolarDates(LocalDate.of(2034, 2, 19), LocalDate.of(2034, 5, 25), LocalDate.of(2034, 9, 27))),
            Map.entry(2035, new LunarHolidaySolarDates(LocalDate.of(2035, 2, 8), LocalDate.of(2035, 5, 15), LocalDate.of(2035, 9, 16))),
            Map.entry(2036, new LunarHolidaySolarDates(LocalDate.of(2036, 1, 28), LocalDate.of(2036, 5, 3), LocalDate.of(2036, 10, 4))),
            Map.entry(2037, new LunarHolidaySolarDates(LocalDate.of(2037, 2, 15), LocalDate.of(2037, 5, 22), LocalDate.of(2037, 9, 24))),
            Map.entry(2038, new LunarHolidaySolarDates(LocalDate.of(2038, 2, 4), LocalDate.of(2038, 5, 11), LocalDate.of(2038, 9, 13))),
            Map.entry(2039, new LunarHolidaySolarDates(LocalDate.of(2039, 1, 24), LocalDate.of(2039, 5, 30), LocalDate.of(2039, 10, 2))),
            Map.entry(2040, new LunarHolidaySolarDates(LocalDate.of(2040, 2, 12), LocalDate.of(2040, 5, 18), LocalDate.of(2040, 9, 20)))
    );

    private record LunarHolidaySolarDates(LocalDate seollal, LocalDate buddha, LocalDate chuseok) {}

    private final Map<LocalDate, String> customHolidays;
    private final ConcurrentHashMap<Integer, List<Holiday>> yearCache = new ConcurrentHashMap<>();

    /**
     * Default constructor with legal holidays only.
     */
    public KoreanHolidayCalculator() {
        this(Map.of());
    }

    /**
     * Constructor allowing additional custom or temporary holidays.
     *
     * @param customHolidays map of date to custom holiday name
     */
    public KoreanHolidayCalculator(Map<LocalDate, String> customHolidays) {
        this.customHolidays = Map.copyOf(Objects.requireNonNull(customHolidays, "customHolidays must not be null"));
    }

    /**
     * Returns the global default instance.
     *
     * @return singleton instance
     */
    public static KoreanHolidayCalculator getInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * Checks if the given date is a Korean public or substitute holiday.
     *
     * @param date calendar date to check
     * @return {@code true} if holiday, {@code false} otherwise
     */
    public boolean isHoliday(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return getHoliday(date).isPresent();
    }

    /**
     * Retrieves the {@link Holiday} detail for the specified date if it is a holiday.
     *
     * @param date calendar date to lookup
     * @return Optional containing Holiday if present
     */
    public Optional<Holiday> getHoliday(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        List<Holiday> holidays = getHolidays(date.getYear());
        for (Holiday holiday : holidays) {
            if (holiday.date().equals(date)) {
                return Optional.of(holiday);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if the given date is specifically a substitute holiday.
     *
     * @param date calendar date to check
     * @return {@code true} if substitute holiday, {@code false} otherwise
     */
    public boolean isSubstituteHoliday(LocalDate date) {
        return getHoliday(date).filter(Holiday::isSubstitute).isPresent();
    }

    /**
     * Retrieves all public, lunar, substitute, and custom holidays for the given year,
     * sorted chronologically.
     *
     * @param year calendar year (recommended 2018–2040)
     * @return unmodifiable list of holidays
     */
    public List<Holiday> getHolidays(int year) {
        return yearCache.computeIfAbsent(year, this::calculateYearHolidays);
    }

    /**
     * Retrieves all holidays in the given date range [startDate, endDate] inclusive.
     *
     * @param start start date inclusive
     * @param end   end date inclusive
     * @return sorted list of holidays within range
     */
    public List<Holiday> getHolidays(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        if (start.isAfter(end)) {
            return List.of();
        }

        List<Holiday> result = new ArrayList<>();
        for (int y = start.getYear(); y <= end.getYear(); y++) {
            for (Holiday h : getHolidays(y)) {
                if (!h.date().isBefore(start) && !h.date().isAfter(end)) {
                    result.add(h);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Creates a new KoreanHolidayCalculator builder for adding custom or election days.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<LocalDate, String> customHolidays = new HashMap<>();

        public Builder addCustomHoliday(LocalDate date, String name) {
            Objects.requireNonNull(date, "date must not be null");
            Objects.requireNonNull(name, "name must not be null");
            this.customHolidays.put(date, name);
            return this;
        }

        public KoreanHolidayCalculator build() {
            return new KoreanHolidayCalculator(this.customHolidays);
        }
    }

    private List<Holiday> calculateYearHolidays(int year) {
        List<Holiday> primary = new ArrayList<>();

        // 1. Solar public holidays
        primary.add(new Holiday("신정", LocalDate.of(year, 1, 1), false, HolidayType.SOLAR));
        primary.add(new Holiday("삼일절", LocalDate.of(year, 3, 1), false, HolidayType.SOLAR));
        primary.add(new Holiday("어린이날", LocalDate.of(year, 5, 5), false, HolidayType.SOLAR));
        primary.add(new Holiday("현충일", LocalDate.of(year, 6, 6), false, HolidayType.SOLAR));
        primary.add(new Holiday("광복절", LocalDate.of(year, 8, 15), false, HolidayType.SOLAR));
        primary.add(new Holiday("개천절", LocalDate.of(year, 10, 3), false, HolidayType.SOLAR));
        primary.add(new Holiday("한글날", LocalDate.of(year, 10, 9), false, HolidayType.SOLAR));
        primary.add(new Holiday("기독탄신일", LocalDate.of(year, 12, 25), false, HolidayType.SOLAR));

        // 2. Lunar public holidays
        LunarHolidaySolarDates lunar = LUNAR_DATA.get(year);
        if (lunar != null) {
            // Seollal 3-day (day before, day of, day after)
            primary.add(new Holiday("설날 전날", lunar.seollal.minusDays(1), false, HolidayType.LUNAR));
            primary.add(new Holiday("설날", lunar.seollal, false, HolidayType.LUNAR));
            primary.add(new Holiday("설날 다음날", lunar.seollal.plusDays(1), false, HolidayType.LUNAR));

            // Buddha's Birthday
            primary.add(new Holiday("부처님오신날", lunar.buddha, false, HolidayType.LUNAR));

            // Chuseok 3-day (day before, day of, day after)
            primary.add(new Holiday("추석 전날", lunar.chuseok.minusDays(1), false, HolidayType.LUNAR));
            primary.add(new Holiday("추석", lunar.chuseok, false, HolidayType.LUNAR));
            primary.add(new Holiday("추석 다음날", lunar.chuseok.plusDays(1), false, HolidayType.LUNAR));
        }

        // 3. Custom/temporary holidays for this year
        for (Map.Entry<LocalDate, String> entry : customHolidays.entrySet()) {
            if (entry.getKey().getYear() == year) {
                primary.add(new Holiday(entry.getValue(), entry.getKey(), false, HolidayType.CUSTOM));
            }
        }

        // Sort primary holidays
        primary.sort(Comparator.comparing(Holiday::date).thenComparing(Holiday::name));

        // 4. Substitute holidays calculation (대체공휴일 법령 제2조의2)
        // Group primary holidays by date to detect overlapping holidays
        Map<LocalDate, List<Holiday>> dateToPrimary = new LinkedHashMap<>();
        for (Holiday h : primary) {
            dateToPrimary.computeIfAbsent(h.date(), k -> new ArrayList<>()).add(h);
        }

        Set<LocalDate> allHolidayDates = new HashSet<>();
        for (Holiday h : primary) {
            allHolidayDates.add(h.date());
        }

        List<Holiday> substituteHolidays = new ArrayList<>();

        for (Holiday h : primary) {
            if (isEligibleForSubstitute(h)) {
                boolean needsSubstitute = false;

                if (isSeollalOrChuseok(h)) {
                    // 설날/추석 연휴: 일요일과 겹치거나 다른 공휴일과 중복될 때
                    if (h.date().getDayOfWeek() == DayOfWeek.SUNDAY) {
                        needsSubstitute = true;
                    } else if (dateToPrimary.get(h.date()).size() > 1 && dateToPrimary.get(h.date()).get(0) != h) {
                        // 중복 공휴일 (두 번째 이후)
                        needsSubstitute = true;
                    }
                } else {
                    // 삼일절, 어린이날, 광복절, 개천절, 한글날, 부처님오신날, 기독탄신일
                    // 토요일, 일요일과 겹치거나 다른 공휴일과 중복될 때
                    DayOfWeek dow = h.date().getDayOfWeek();
                    if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                        needsSubstitute = true;
                    } else if (dateToPrimary.get(h.date()).size() > 1 && dateToPrimary.get(h.date()).get(0) != h) {
                        needsSubstitute = true;
                    }
                }

                if (needsSubstitute) {
                    LocalDate candidate = findNextSubstituteWeekday(h.date(), allHolidayDates);
                    Holiday sub = new Holiday("대체공휴일 (" + h.name() + ")", candidate, true, HolidayType.SUBSTITUTE);
                    substituteHolidays.add(sub);
                    allHolidayDates.add(candidate);
                }
            }
        }

        List<Holiday> allHolidays = new ArrayList<>(primary);
        allHolidays.addAll(substituteHolidays);
        allHolidays.sort(Comparator.comparing(Holiday::date).thenComparing(Holiday::name));

        return Collections.unmodifiableList(allHolidays);
    }

    private static boolean isEligibleForSubstitute(Holiday holiday) {
        String name = holiday.name();
        // 신정(1/1)과 현충일(6/6)은 제외
        if ("신정".equals(name) || "현충일".equals(name)) {
            return false;
        }
        return "삼일절".equals(name)
                || "어린이날".equals(name)
                || "광복절".equals(name)
                || "개천절".equals(name)
                || "한글날".equals(name)
                || "기독탄신일".equals(name)
                || "부처님오신날".equals(name)
                || name.startsWith("설날")
                || name.startsWith("추석");
    }

    private static boolean isSeollalOrChuseok(Holiday holiday) {
        String name = holiday.name();
        return name.startsWith("설날") || name.startsWith("추석");
    }

    private static LocalDate findNextSubstituteWeekday(LocalDate from, Set<LocalDate> existingHolidayDates) {
        LocalDate current = from.plusDays(1);
        while (true) {
            DayOfWeek dow = current.getDayOfWeek();
            boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
            if (!isWeekend && !existingHolidayDates.contains(current)) {
                return current;
            }
            current = current.plusDays(1);
        }
    }
}
