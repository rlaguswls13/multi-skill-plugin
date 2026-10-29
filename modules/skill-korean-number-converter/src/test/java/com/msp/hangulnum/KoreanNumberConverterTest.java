package com.msp.hangulnum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KoreanNumberConverterTest {

    @Test
    @DisplayName("Convert zero in standard Hangul and formal Hanja")
    void testZero() {
        assertEquals("영", KoreanNumberConverter.toHangul(0));
        assertEquals("金 零 圓整", KoreanNumberConverter.toHanjaFormal(0));
    }

    @Test
    @DisplayName("Convert numbers without explicit '일' in everyday spoken mode")
    void testStandardHangulNumbers() {
        assertEquals("십", KoreanNumberConverter.toHangul(10));
        assertEquals("백십", KoreanNumberConverter.toHangul(110));
        assertEquals("천백십일", KoreanNumberConverter.toHangul(1111));
        assertEquals("일만 이천삼백사십오", KoreanNumberConverter.toHangul(12345));
    }

    @Test
    @DisplayName("Convert large numbers across 조, 억, 만 units")
    void testLargeNumbers() {
        // 1,234,567,890,123
        long num = 1_234_567_890_123L;
        String hangul = KoreanNumberConverter.toHangul(num);
        assertEquals("일조 이천삼백사십오억 육천칠백팔십구만 백이십삼", hangul);
    }

    @Test
    @DisplayName("Currency receipt typography mode outputs exact formal pattern")
    void testCurrencyReceiptMode() {
        long amount = 12_345_000L;
        String formatted = KoreanNumberConverter.toCurrency(amount);
        assertEquals("금 일천이백삼십사만 오천 원정", formatted);
    }

    @Test
    @DisplayName("Formal financial Hanja mode replaces numbers with alteration-resistant characters")
    void testFormalHanja() {
        long amount = 12_345L;
        String formal = KoreanNumberConverter.toHanjaFormal(amount);
        // 12,345 = 壹萬 貳阡參佰肆拾伍
        assertEquals("金 壹萬 貳阡參佰肆拾伍 圓整", formal);
    }

    @Test
    @DisplayName("Standard basic Hanja mode converts to 一, 二, 三...")
    void testStandardHanja() {
        assertEquals("一萬 二千三百四十五", KoreanNumberConverter.toHanja(12345));
    }

    @Test
    @DisplayName("Negative numbers are prefixed with 마이너스 or 負")
    void testNegativeNumbers() {
        assertEquals("마이너스 오천", KoreanNumberConverter.toHangul(-5000));
    }

    @Test
    @DisplayName("Pure Korean counting words (하나, 둘, 셋, 열, 스물)")
    void testPureKoreanCounts() {
        assertEquals("하나", KoreanNumberConverter.toPureKoreanCount(1));
        assertEquals("열", KoreanNumberConverter.toPureKoreanCount(10));
        assertEquals("스물", KoreanNumberConverter.toPureKoreanCount(20));
        assertEquals("스물셋", KoreanNumberConverter.toPureKoreanCount(23));
        assertEquals("아흔아홉", KoreanNumberConverter.toPureKoreanCount(99));
    }

    @Test
    @DisplayName("Pure Korean numeral modifiers before unit nouns (한 개, 두 개, 스무 개, 스물한 개)")
    void testPureKoreanUnits() {
        assertEquals("한 개", KoreanNumberConverter.toPureKoreanCount(1, "개"));
        assertEquals("두 명", KoreanNumberConverter.toPureKoreanCount(2, "명"));
        assertEquals("세 병", KoreanNumberConverter.toPureKoreanCount(3, "병"));
        assertEquals("네 마리", KoreanNumberConverter.toPureKoreanCount(4, "마리"));
        assertEquals("다섯 권", KoreanNumberConverter.toPureKoreanCount(5, "권"));
        assertEquals("스무 개", KoreanNumberConverter.toPureKoreanCount(20, "개"));
        assertEquals("스물한 개", KoreanNumberConverter.toPureKoreanCount(21, "개"));
    }

    @Test
    @DisplayName("Pure Korean counting outside 1..99 throws IllegalArgumentException")
    void testPureKoreanOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> KoreanNumberConverter.toPureKoreanCount(0));
        assertThrows(IllegalArgumentException.class, () -> KoreanNumberConverter.toPureKoreanCount(100));
        assertThrows(IllegalArgumentException.class, () -> KoreanNumberConverter.toPureKoreanCount(-1));
    }
}
