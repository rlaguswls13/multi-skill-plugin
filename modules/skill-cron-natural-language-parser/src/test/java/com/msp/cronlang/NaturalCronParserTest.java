package com.msp.cronlang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class NaturalCronParserTest {

    @ParameterizedTest(name = "Sentence \"{0}\" -> Cron \"{1}\"")
    @CsvSource({
            "매일 09:00, 0 9 * * *",
            "every day at 09:00, 0 9 * * *",
            "daily at 09:00, 0 9 * * *",
            "매주 월요일 14:30, 30 14 * * 1",
            "every monday at 14:30, 30 14 * * 1",
            "every mon at 14:30, 30 14 * * 1",
            "매주 금요일 18:00, 0 18 * * 5",
            "every friday at 18:00, 0 18 * * 5",
            "매주 일요일 07:00, 0 7 * * 0",
            "every sunday at 07:00, 0 7 * * 0",
            "매시간, 0 * * * *",
            "매시간 정각, 0 * * * *",
            "every hour, 0 * * * *",
            "hourly, 0 * * * *",
            "15분마다, */15 * * * *",
            "every 15 minutes, */15 * * * *",
            "every 15 mins, */15 * * * *",
            "매 5분, */5 * * * *",
            "2시간마다, 0 */2 * * *",
            "every 2 hours, 0 */2 * * *",
            "매월 1일 00:00, 0 0 1 * *",
            "매월 15일 23:59, 59 23 15 * *",
            "every 1st day of month at 00:00, 0 0 1 * *",
            "every 15th day of month at 12:00, 0 12 15 * *",
            "매주 평일 08:00, 0 8 * * 1-5",
            "every weekday at 08:00, 0 8 * * 1-5",
            "매주 주말 10:00, '0 10 * * 0,6'",
            "every weekend at 10:00, '0 10 * * 0,6'",
            "매일 자정, 0 0 * * *",
            "midnight, 0 0 * * *",
            "매일 정오, 0 12 * * *",
            "noon, 0 12 * * *"
    })
    void testStandardPatterns(String naturalSentence, String expectedCron) {
        assertEquals(expectedCron, NaturalCronParser.parse(naturalSentence));
        assertEquals(expectedCron, NaturalCronParser.toCron(naturalSentence));
    }

    @Test
    @DisplayName("Throws IllegalArgumentException on null, blank or unrecognized sentences")
    void testUnrecognizedSentences() {
        assertThrows(NullPointerException.class, () -> NaturalCronParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse("tomorrow afternoon"));
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse("hello world"));
    }

    @Test
    @DisplayName("Throws IllegalArgumentException on invalid ranges")
    void testInvalidTimeRanges() {
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse("매일 25:00"));
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse("매일 12:60"));
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse("매월 32일 10:00"));
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse("0분마다"));
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse("70분마다"));
        assertThrows(IllegalArgumentException.class, () -> NaturalCronParser.parse("25시간마다"));
    }
}
