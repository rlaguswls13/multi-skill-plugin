package com.msp.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    @DisplayName("Money addition and subtraction test")
    void testAddSubtract() {
        Money m1 = Money.of(15000, Currency.KRW);
        Money m2 = Money.of(3500, Currency.KRW);

        Money sum = m1.plus(m2);
        assertEquals(BigDecimal.valueOf(18500), sum.amount());
        assertEquals("₩18,500", sum.format());

        Money diff = m1.minus(m2);
        assertEquals(BigDecimal.valueOf(11500), diff.amount());
    }

    @Test
    @DisplayName("Currency scale and rounding test (USD vs KRW)")
    void testScaleRounding() {
        Money usd = Money.of(10.556, Currency.USD); // 2 fraction digits, HALF_EVEN -> 10.56
        assertEquals(new BigDecimal("10.56"), usd.amount());
        assertEquals("$10.56", usd.format());

        Money krw = Money.of(10.556, Currency.KRW); // 0 fraction digits -> 11
        assertEquals(new BigDecimal("11"), krw.amount());
        assertEquals("₩11", krw.format());
    }

    @Test
    @DisplayName("Currency conversion test")
    void testConversion() {
        Money usd = Money.of(100, Currency.USD);
        BigDecimal exchangeRate = new BigDecimal("1300.50"); // 1 USD = 1300.50 KRW
        Money krw = usd.convert(Currency.KRW, exchangeRate);

        assertEquals(Currency.KRW, krw.currency());
        assertEquals(new BigDecimal("130050"), krw.amount());
        assertEquals("₩130,050", krw.format());
    }

    @Test
    @DisplayName("Currency mismatch exception test")
    void testMismatch() {
        Money usd = Money.of(10, Currency.USD);
        Money krw = Money.of(1000, Currency.KRW);

        assertThrows(IllegalArgumentException.class, () -> usd.plus(krw));
    }
}
