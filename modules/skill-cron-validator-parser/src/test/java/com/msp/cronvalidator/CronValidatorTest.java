package com.msp.cronvalidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CronValidatorTest {

    @Test
    @DisplayName("5필드 표준 유닉스 Cron 검증")
    void testStandardFiveFieldCron() {
        CronValidationResult res1 = CronValidator.validate("*/5 * * * *");
        assertTrue(res1.valid());
        assertEquals(5, res1.fields().size());
        assertEquals(CronFieldType.MINUTE, res1.fields().get(0).type());
        assertEquals("Every 5 minutes", CronValidator.describe("*/5 * * * *"));

        CronValidationResult res2 = CronValidator.validate("30 4 * * *");
        assertTrue(res2.valid());
        assertEquals("At 04:30 every day", CronValidator.describe("30 4 * * *"));
    }

    @Test
    @DisplayName("6필드 초 포함 Cron 검증 및 자연어 요약")
    void testSixFieldCron() {
        CronValidationResult res = CronValidator.validate("0 0 12 * * ?");
        assertTrue(res.valid());
        assertEquals(6, res.fields().size());
        assertEquals(CronFieldType.SECOND, res.fields().get(0).type());
        assertEquals("At 12:00:00 every day", CronValidator.describe("0 0 12 * * ?"));

        assertTrue(CronValidator.isValid("0 15 10 ? * 6L"));
        assertTrue(CronValidator.isValid("0 0 8 15W * ?"));
        assertTrue(CronValidator.isValid("0 0 10 ? * 5#3")); // 3rd Friday of the month
    }

    @Test
    @DisplayName("월 및 요일 영문 약어(JAN-DEC, MON-FRI) 지원 검증")
    void testAliases() {
        CronValidationResult res1 = CronValidator.validate("0 9 * * MON-FRI");
        assertTrue(res1.valid());
        assertEquals("At 09:00, only on Monday through Friday", CronValidator.describe("0 9 * * MON-FRI"));

        CronValidationResult res2 = CronValidator.validate("0 9 * JAN-MAR MON-FRI");
        assertTrue(res2.valid());
        assertEquals("At 09:00, in JAN-MAR, only on Monday through Friday", CronValidator.describe("0 9 * JAN-MAR MON-FRI"));
    }

    @Test
    @DisplayName("유효하지 않은 필드 개수 및 기호 방어 검증")
    void testInvalidExpressions() {
        // 필드 개수 불일치
        CronValidationResult tooFew = CronValidator.validate("* * *");
        assertFalse(tooFew.valid());
        assertTrue(tooFew.errorMessage().contains("expected 5 or 6"));

        // 분 필드에 '?' 사용 불가
        CronValidationResult invalidQuestion = CronValidator.validate("? * * * *");
        assertFalse(invalidQuestion.valid());
        assertTrue(invalidQuestion.errorMessage().contains("only allowed for day-of-month and day-of-week"));

        // 범위 초과 (시간 25 불가)
        CronValidationResult outOfBounds = CronValidator.validate("0 25 * * *");
        assertFalse(outOfBounds.valid());
        assertTrue(outOfBounds.errorMessage().contains("out of bounds"));

        // 시작값 > 종료값 (15-10 불가)
        CronValidationResult invertedRange = CronValidator.validate("15-10 * * * *");
        assertFalse(invertedRange.valid());
        assertTrue(invertedRange.errorMessage().contains("must be <="));

        // 0 이하 스텝 불가
        CronValidationResult invalidStep = CronValidator.validate("*/0 * * * *");
        assertFalse(invalidStep.valid());
        assertTrue(invalidStep.errorMessage().contains("must be positive"));
    }

    @Test
    @DisplayName("describe() 유효하지 않은 식에 대한 에러 메시지 반환 검증")
    void testDescribeInvalid() {
        String desc = CronValidator.describe("invalid cron expr");
        assertTrue(desc.startsWith("Invalid cron expression:"));
    }
}
