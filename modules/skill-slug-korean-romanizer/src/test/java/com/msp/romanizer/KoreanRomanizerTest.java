package com.msp.romanizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KoreanRomanizerTest {

    @Test
    @DisplayName("Romanize Korean words accurately according to standard rules")
    void testRomanize() {
        // 안 = ㅇ(null) + ㅏ(a) + ㄴ(n) = an
        // 녕 = ㄴ(n) + ㅕ(yeo) + ㅇ(ng) = nyeong
        // 하 = ㅎ(h) + ㅏ(a) = ha
        // 세 = ㅅ(s) + ㅔ(e) = se
        // 요 = ㅇ(null) + ㅛ(yo) = yo
        assertEquals("annyeonghaseyo", KoreanRomanizer.romanize("안녕하세요"));

        // 한국어
        // 한 = h + a + n = han
        // 국 = g + u + k = guk
        // 어 = eo = eo
        assertEquals("hangukeo", KoreanRomanizer.romanize("한국어"));

        // 혼합 문자
        assertEquals("Hello world seoul", KoreanRomanizer.romanize("Hello world 서울"));
    }

    @Test
    @DisplayName("Generate URL-safe slug from mixed Korean and English title")
    void testToSlug() {
        String title = "스프링 부트 3.0 & 카프카 실전 가이드!";
        String slug = KoreanRomanizer.toSlug(title);

        // 스프링 = seupeuring, 부트 =証buteu, 카프카 = kapeuka, 실전 = siljeon, 가이드 = gaideu
        assertFalse(slug.contains(" "));
        assertFalse(slug.contains("!"));
        assertFalse(slug.contains("&"));
        assertTrue(slug.contains("3-0"));
        assertTrue(slug.startsWith("seupeuring-buteu-3-0"));
    }
}
