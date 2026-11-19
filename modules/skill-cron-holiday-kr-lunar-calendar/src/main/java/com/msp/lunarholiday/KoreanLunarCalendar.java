package com.msp.lunarholiday;

import java.time.LocalDate;
import java.util.Map;

/**
 * Astronomical solar conversion tables for major Korean lunar holidays (설날, 부처님오신날, 추석).
 * Covers years 2015 through 2035.
 */
public final class KoreanLunarCalendar {

    public record LunarDates(LocalDate seollal, LocalDate buddha, LocalDate chuseok) {}

    private static final Map<Integer, LunarDates> TABLE = Map.ofEntries(
            Map.entry(2015, new LunarDates(LocalDate.of(2015, 2, 19), LocalDate.of(2015, 5, 25), LocalDate.of(2015, 9, 27))),
            Map.entry(2016, new LunarDates(LocalDate.of(2016, 2, 8), LocalDate.of(2016, 5, 14), LocalDate.of(2016, 9, 15))),
            Map.entry(2017, new LunarDates(LocalDate.of(2017, 1, 28), LocalDate.of(2017, 5, 3), LocalDate.of(2017, 10, 4))),
            Map.entry(2018, new LunarDates(LocalDate.of(2018, 2, 16), LocalDate.of(2018, 5, 22), LocalDate.of(2018, 9, 24))),
            Map.entry(2019, new LunarDates(LocalDate.of(2019, 2, 5), LocalDate.of(2019, 5, 12), LocalDate.of(2019, 9, 13))),
            Map.entry(2020, new LunarDates(LocalDate.of(2020, 1, 25), LocalDate.of(2020, 4, 30), LocalDate.of(2020, 10, 1))),
            Map.entry(2021, new LunarDates(LocalDate.of(2021, 2, 12), LocalDate.of(2021, 5, 19), LocalDate.of(2021, 9, 21))),
            Map.entry(2022, new LunarDates(LocalDate.of(2022, 2, 1), LocalDate.of(2022, 5, 8), LocalDate.of(2022, 9, 10))),
            Map.entry(2023, new LunarDates(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 5, 27), LocalDate.of(2023, 9, 29))),
            Map.entry(2024, new LunarDates(LocalDate.of(2024, 2, 10), LocalDate.of(2024, 5, 15), LocalDate.of(2024, 9, 17))),
            Map.entry(2025, new LunarDates(LocalDate.of(2025, 1, 29), LocalDate.of(2025, 5, 5), LocalDate.of(2025, 10, 6))),
            Map.entry(2026, new LunarDates(LocalDate.of(2026, 2, 17), LocalDate.of(2026, 5, 24), LocalDate.of(2026, 9, 25))),
            Map.entry(2027, new LunarDates(LocalDate.of(2027, 2, 7), LocalDate.of(2027, 5, 13), LocalDate.of(2027, 9, 15))),
            Map.entry(2028, new LunarDates(LocalDate.of(2028, 1, 26), LocalDate.of(2028, 5, 2), LocalDate.of(2028, 10, 3))),
            Map.entry(2029, new LunarDates(LocalDate.of(2029, 2, 13), LocalDate.of(2029, 5, 20), LocalDate.of(2029, 9, 22))),
            Map.entry(2030, new LunarDates(LocalDate.of(2030, 2, 3), LocalDate.of(2030, 5, 9), LocalDate.of(2030, 9, 12))),
            Map.entry(2031, new LunarDates(LocalDate.of(2031, 1, 23), LocalDate.of(2031, 5, 28), LocalDate.of(2031, 10, 1))),
            Map.entry(2032, new LunarDates(LocalDate.of(2032, 2, 11), LocalDate.of(2032, 5, 16), LocalDate.of(2032, 9, 19))),
            Map.entry(2033, new LunarDates(LocalDate.of(2033, 1, 31), LocalDate.of(2033, 5, 6), LocalDate.of(2033, 9, 8))),
            Map.entry(2034, new LunarDates(LocalDate.of(2034, 2, 19), LocalDate.of(2034, 5, 25), LocalDate.of(2034, 9, 27))),
            Map.entry(2035, new LunarDates(LocalDate.of(2035, 2, 8), LocalDate.of(2035, 5, 15), LocalDate.of(2035, 9, 16)))
    );

    private KoreanLunarCalendar() {}

    public static boolean isSupported(int year) {
        return TABLE.containsKey(year);
    }

    public static LunarDates getDates(int year) {
        LunarDates dates = TABLE.get(year);
        if (dates == null) {
            throw new IllegalArgumentException("Year " + year + " is outside supported range (2015-2035)");
        }
        return dates;
    }

    public static LocalDate getSeollal(int year) {
        return getDates(year).seollal();
    }

    public static LocalDate getBuddhasBirthday(int year) {
        return getDates(year).buddha();
    }

    public static LocalDate getChuseok(int year) {
        return getDates(year).chuseok();
    }
}
