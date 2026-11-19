package com.msp.rrule;

import java.time.*;
import java.util.*;

/**
 * Representation and evaluator of an iCalendar RFC 5545 Recurrence Rule (RRULE).
 */
public final class RecurrenceRule {

    private final Frequency freq;
    private final int interval;
    private final Integer count;
    private final ZonedDateTime until;
    private final List<ByDay> byDay;
    private final List<Integer> byMonth;
    private final List<Integer> byMonthDay;
    private final List<Integer> bySetPos;
    private final DayOfWeek wkst;

    private RecurrenceRule(Frequency freq, int interval, Integer count, ZonedDateTime until,
                           List<ByDay> byDay, List<Integer> byMonth, List<Integer> byMonthDay,
                           List<Integer> bySetPos, DayOfWeek wkst) {
        this.freq = Objects.requireNonNull(freq, "freq must not be null");
        this.interval = Math.max(1, interval);
        this.count = count;
        this.until = until;
        this.byDay = byDay != null ? List.copyOf(byDay) : List.of();
        this.byMonth = byMonth != null ? List.copyOf(byMonth) : List.of();
        this.byMonthDay = byMonthDay != null ? List.copyOf(byMonthDay) : List.of();
        this.bySetPos = bySetPos != null ? List.copyOf(bySetPos) : List.of();
        this.wkst = wkst != null ? wkst : DayOfWeek.MONDAY;
    }

    public Frequency getFreq() {
        return freq;
    }

    public int getInterval() {
        return interval;
    }

    public Optional<Integer> getCount() {
        return Optional.ofNullable(count);
    }

    public Optional<ZonedDateTime> getUntil() {
        return Optional.ofNullable(until);
    }

    public List<ByDay> getByDay() {
        return byDay;
    }

    public List<Integer> getByMonth() {
        return byMonth;
    }

    public List<Integer> getByMonthDay() {
        return byMonthDay;
    }

    public List<Integer> getBySetPos() {
        return bySetPos;
    }

    public DayOfWeek getWkst() {
        return wkst;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RecurrenceRule parse(String rruleString) {
        return parse(rruleString, ZoneOffset.UTC);
    }

    public static RecurrenceRule parse(String rruleString, ZoneId defaultZone) {
        Objects.requireNonNull(rruleString, "rruleString must not be null");
        String s = rruleString.trim();
        if (s.toUpperCase().startsWith("RRULE:")) {
            s = s.substring(6).trim();
        }

        Builder b = builder();
        String[] parts = s.split(";");
        for (String part : parts) {
            if (part.isBlank()) continue;
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().toUpperCase();
            String val = kv[1].trim();

            switch (key) {
                case "FREQ" -> b.frequency(Frequency.valueOf(val.toUpperCase()));
                case "INTERVAL" -> b.interval(Integer.parseInt(val));
                case "COUNT" -> b.count(Integer.parseInt(val));
                case "UNTIL" -> b.until(DateTimeUtils.parseDateTime(val, defaultZone));
                case "WKST" -> b.wkst(parseWkst(val));
                case "BYDAY" -> {
                    String[] days = val.split(",");
                    for (String d : days) {
                        b.byDay(ByDay.parse(d));
                    }
                }
                case "BYMONTH" -> {
                    String[] months = val.split(",");
                    for (String m : months) {
                        b.byMonth(Integer.parseInt(m.trim()));
                    }
                }
                case "BYMONTHDAY" -> {
                    String[] mdays = val.split(",");
                    for (String md : mdays) {
                        b.byMonthDay(Integer.parseInt(md.trim()));
                    }
                }
                case "BYSETPOS" -> {
                    String[] pos = val.split(",");
                    for (String p : pos) {
                        b.bySetPos(Integer.parseInt(p.trim()));
                    }
                }
            }
        }
        return b.build();
    }

    private static DayOfWeek parseWkst(String s) {
        return switch (s.toUpperCase()) {
            case "MO" -> DayOfWeek.MONDAY;
            case "TU" -> DayOfWeek.TUESDAY;
            case "WE" -> DayOfWeek.WEDNESDAY;
            case "TH" -> DayOfWeek.THURSDAY;
            case "FR" -> DayOfWeek.FRIDAY;
            case "SA" -> DayOfWeek.SATURDAY;
            case "SU" -> DayOfWeek.SUNDAY;
            default -> DayOfWeek.MONDAY;
        };
    }

    public String toRRuleString() {
        StringBuilder sb = new StringBuilder("FREQ=").append(freq.name());
        if (interval > 1) {
            sb.append(";INTERVAL=").append(interval);
        }
        if (count != null) {
            sb.append(";COUNT=").append(count);
        }
        if (until != null) {
            sb.append(";UNTIL=").append(until.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")));
        }
        if (!byMonth.isEmpty()) {
            sb.append(";BYMONTH=").append(String.join(",", byMonth.stream().map(String::valueOf).toList()));
        }
        if (!byDay.isEmpty()) {
            sb.append(";BYDAY=").append(String.join(",", byDay.stream().map(ByDay::toString).toList()));
        }
        if (!byMonthDay.isEmpty()) {
            sb.append(";BYMONTHDAY=").append(String.join(",", byMonthDay.stream().map(String::valueOf).toList()));
        }
        if (!bySetPos.isEmpty()) {
            sb.append(";BYSETPOS=").append(String.join(",", bySetPos.stream().map(String::valueOf).toList()));
        }
        if (wkst != DayOfWeek.MONDAY) {
            sb.append(";WKST=").append(ByDay.toDayOfWeekCode(wkst));
        }
        return sb.toString();
    }

    /**
     * Calculates occurrences starting from 'start' within the date range [from, to] inclusive.
     */
    public List<ZonedDateTime> getOccurrences(ZonedDateTime start, ZonedDateTime from, ZonedDateTime to, int maxResults) {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (maxResults <= 0) {
            return Collections.emptyList();
        }

        List<ZonedDateTime> results = new ArrayList<>();
        Iterator<ZonedDateTime> iter = iterator(start);

        while (iter.hasNext() && results.size() < maxResults) {
            ZonedDateTime occurrence = iter.next();
            if (occurrence.isAfter(to)) {
                break;
            }
            if (!occurrence.isBefore(from)) {
                results.add(occurrence);
            }
        }
        return results;
    }

    /**
     * Generates up to count occurrences from start.
     */
    public List<ZonedDateTime> getOccurrences(ZonedDateTime start, int maxResults) {
        Objects.requireNonNull(start, "start must not be null");
        if (maxResults <= 0) return Collections.emptyList();
        List<ZonedDateTime> results = new ArrayList<>();
        Iterator<ZonedDateTime> iter = iterator(start);
        while (iter.hasNext() && results.size() < maxResults) {
            results.add(iter.next());
        }
        return results;
    }

    /**
     * Returns an iterator that lazily generates occurrences from start according to the recurrence rule.
     */
    public Iterator<ZonedDateTime> iterator(ZonedDateTime start) {
        return new OccurrenceIterator(start);
    }

    private class OccurrenceIterator implements Iterator<ZonedDateTime> {
        private final ZonedDateTime start;
        private final LocalTime time;
        private final ZoneId zone;
        private int totalGenerated = 0;
        private LocalDate currentDate;
        private final Queue<LocalDate> pendingDates = new ArrayDeque<>();
        private LocalDate nextDate = null;
        private int cycle = 0;

        OccurrenceIterator(ZonedDateTime start) {
            this.start = start;
            this.time = start.toLocalTime();
            this.zone = start.getZone();
            this.currentDate = start.toLocalDate();
            advance();
        }

        private void advance() {
            nextDate = null;
            if (count != null && totalGenerated >= count) {
                return;
            }

            while (pendingDates.isEmpty() && cycle < 100000) {
                generateCycleDates();
                cycle++;
            }

            while (!pendingDates.isEmpty()) {
                LocalDate cand = pendingDates.poll();
                ZonedDateTime zdt = cand.atTime(time).atZone(zone);
                if (until != null && zdt.isAfter(until)) {
                    pendingDates.clear();
                    return;
                }
                if (!cand.isBefore(start.toLocalDate())) {
                    nextDate = cand;
                    totalGenerated++;
                    return;
                }
            }
        }

        private void generateCycleDates() {
            switch (freq) {
                case DAILY -> {
                    LocalDate d = currentDate.plusDays((long) cycle * interval);
                    if (matchesFilters(d)) {
                        pendingDates.add(d);
                    }
                }
                case WEEKLY -> {
                    LocalDate weekBase = currentDate.plusWeeks((long) cycle * interval);
                    // Find start of week according to wkst
                    LocalDate weekStart = weekBase.with(java.time.temporal.TemporalAdjusters.previousOrSame(wkst));
                    List<LocalDate> daysInWeek = new ArrayList<>();
                    if (byDay.isEmpty()) {
                        LocalDate d = weekStart.plusDays((start.getDayOfWeek().getValue() - wkst.getValue() + 7) % 7);
                        daysInWeek.add(d);
                    } else {
                        for (ByDay bd : byDay) {
                            int offset = (bd.dayOfWeek().getValue() - wkst.getValue() + 7) % 7;
                            daysInWeek.add(weekStart.plusDays(offset));
                        }
                        daysInWeek.sort(Comparator.naturalOrder());
                    }
                    for (LocalDate d : daysInWeek) {
                        if (matchesMonthFilter(d)) {
                            pendingDates.add(d);
                        }
                    }
                }
                case MONTHLY -> {
                    LocalDate monthBase = currentDate.plusMonths((long) cycle * interval);
                    YearMonth ym = YearMonth.from(monthBase);
                    if (!matchesMonthFilter(monthBase)) {
                        return;
                    }

                    List<LocalDate> candidates = new ArrayList<>();
                    if (!byDay.isEmpty()) {
                        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
                            LocalDate d = ym.atDay(day);
                            for (ByDay bd : byDay) {
                                if (bd.matches(d)) {
                                    candidates.add(d);
                                    break;
                                }
                            }
                        }
                    } else if (!byMonthDay.isEmpty()) {
                        for (int md : byMonthDay) {
                            int actualDay = md > 0 ? md : ym.lengthOfMonth() + 1 + md;
                            if (actualDay >= 1 && actualDay <= ym.lengthOfMonth()) {
                                candidates.add(ym.atDay(actualDay));
                            }
                        }
                        candidates.sort(Comparator.naturalOrder());
                    } else {
                        int day = Math.min(start.getDayOfMonth(), ym.lengthOfMonth());
                        candidates.add(ym.atDay(day));
                    }

                    if (!bySetPos.isEmpty() && !candidates.isEmpty()) {
                        List<LocalDate> filtered = new ArrayList<>();
                        for (int pos : bySetPos) {
                            int idx = pos > 0 ? pos - 1 : candidates.size() + pos;
                            if (idx >= 0 && idx < candidates.size()) {
                                filtered.add(candidates.get(idx));
                            }
                        }
                        candidates = filtered;
                    }

                    pendingDates.addAll(candidates);
                }
                case YEARLY -> {
                    int targetYear = currentDate.getYear() + cycle * interval;
                    List<Integer> months = byMonth.isEmpty() ? List.of(start.getMonthValue()) : byMonth;
                    for (int m : months) {
                        YearMonth ym = YearMonth.of(targetYear, m);
                        int day = Math.min(start.getDayOfMonth(), ym.lengthOfMonth());
                        pendingDates.add(ym.atDay(day));
                    }
                }
            }
        }

        private boolean matchesFilters(LocalDate d) {
            if (!matchesMonthFilter(d)) return false;
            if (!byDay.isEmpty()) {
                return byDay.stream().anyMatch(bd -> bd.matches(d));
            }
            return true;
        }

        private boolean matchesMonthFilter(LocalDate d) {
            return byMonth.isEmpty() || byMonth.contains(d.getMonthValue());
        }

        @Override
        public boolean hasNext() {
            return nextDate != null;
        }

        @Override
        public ZonedDateTime next() {
            if (nextDate == null) {
                throw new NoSuchElementException();
            }
            ZonedDateTime res = nextDate.atTime(time).atZone(zone);
            advance();
            return res;
        }
    }

    public static final class Builder {
        private Frequency freq = Frequency.DAILY;
        private int interval = 1;
        private Integer count;
        private ZonedDateTime until;
        private final List<ByDay> byDay = new ArrayList<>();
        private final List<Integer> byMonth = new ArrayList<>();
        private final List<Integer> byMonthDay = new ArrayList<>();
        private final List<Integer> bySetPos = new ArrayList<>();
        private DayOfWeek wkst = DayOfWeek.MONDAY;

        public Builder frequency(Frequency freq) {
            this.freq = freq;
            return this;
        }

        public Builder interval(int interval) {
            this.interval = interval;
            return this;
        }

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder until(ZonedDateTime until) {
            this.until = until;
            return this;
        }

        public Builder byDay(ByDay... days) {
            if (days != null) {
                Collections.addAll(this.byDay, days);
            }
            return this;
        }

        public Builder byDay(DayOfWeek... days) {
            if (days != null) {
                for (DayOfWeek d : days) {
                    this.byDay.add(ByDay.of(d));
                }
            }
            return this;
        }

        public Builder byMonth(Integer... months) {
            if (months != null) {
                Collections.addAll(this.byMonth, months);
            }
            return this;
        }

        public Builder byMonthDay(Integer... days) {
            if (days != null) {
                Collections.addAll(this.byMonthDay, days);
            }
            return this;
        }

        public Builder bySetPos(Integer... pos) {
            if (pos != null) {
                Collections.addAll(this.bySetPos, pos);
            }
            return this;
        }

        public Builder wkst(DayOfWeek wkst) {
            this.wkst = wkst;
            return this;
        }

        public RecurrenceRule build() {
            return new RecurrenceRule(freq, interval, count, until, byDay, byMonth, byMonthDay, bySetPos, wkst);
        }
    }
}
