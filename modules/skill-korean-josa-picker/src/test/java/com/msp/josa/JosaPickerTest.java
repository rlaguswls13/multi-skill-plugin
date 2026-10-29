package com.msp.josa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JosaPicker Tests")
class JosaPickerTest {

    @ParameterizedTest
    @CsvSource({
            "철수, 는",
            "영희, 는",
            "사과, 는",
            "선생님, 은",
            "수박, 은",
            "한국, 은"
    })
    @DisplayName("EUN_NEUN (은/는) pick tests")
    void testEunNeun(String word, String expectedJosa) {
        assertEquals(expectedJosa, JosaPicker.pick(word, JosaType.EUN_NEUN));
        assertEquals(word + expectedJosa, JosaPicker.attach(word, JosaType.EUN_NEUN));
    }

    @ParameterizedTest
    @CsvSource({
            "철수, 가",
            "사과, 가",
            "선생님, 이",
            "꽃, 이"
    })
    @DisplayName("I_GA (이/가) pick tests")
    void testIGa(String word, String expectedJosa) {
        assertEquals(expectedJosa, JosaPicker.pick(word, JosaType.I_GA));
    }

    @ParameterizedTest
    @CsvSource({
            "사과, 를",
            "차, 를",
            "밥, 을",
            "책, 을"
    })
    @DisplayName("EUL_REUL (을/를) pick tests")
    void testEulReul(String word, String expectedJosa) {
        assertEquals(expectedJosa, JosaPicker.pick(word, JosaType.EUL_REUL));
    }

    @ParameterizedTest
    @CsvSource({
            "사과, 와",
            "바다, 와",
            "수박, 과",
            "집, 과"
    })
    @DisplayName("GWA_WA (과/와) pick tests")
    void testGwaWa(String word, String expectedJosa) {
        assertEquals(expectedJosa, JosaPicker.pick(word, JosaType.GWA_WA));
    }

    @ParameterizedTest
    @CsvSource({
            "바다, 로",
            "차, 로",
            "서울, 로",   // 'ㄹ' 받침 특수 케이스: 서울로 (O), 서울으로 (X)
            "칼, 로",     // 칼로 (O)
            "길, 로",     // 길로 (O)
            "마을, 로",   // 마을로 (O)
            "집, 으로",   // 일반 받침: 집으로
            "부산, 으로", // 일반 받침: 부산으로
            "식당, 으로"  // 일반 받침: 식당으로
    })
    @DisplayName("EURO_RO (으로/로) special Rieul handling tests")
    void testEuroRo(String word, String expectedJosa) {
        assertEquals(expectedJosa, JosaPicker.pick(word, JosaType.EURO_RO));
        assertEquals(word + expectedJosa, JosaPicker.attach(word, JosaType.EURO_RO));
    }

    @ParameterizedTest
    @CsvSource({
            "사과, 나",
            "수박, 이나"
    })
    @DisplayName("INA_NA (이나/나) pick tests")
    void testInaNa(String word, String expectedJosa) {
        assertEquals(expectedJosa, JosaPicker.pick(word, JosaType.INA_NA));
    }

    @ParameterizedTest
    @CsvSource({
            "사과, 예요",
            "수박, 이에요"
    })
    @DisplayName("IE_YE (이에요/예요) pick tests")
    void testIeYe(String word, String expectedJosa) {
        assertEquals(expectedJosa, JosaPicker.pick(word, JosaType.IE_YE));
    }

    @ParameterizedTest
    @CsvSource({
            "철수, 야",
            "길동, 아"
    })
    @DisplayName("A_YA (아/야) pick tests")
    void testAYa(String word, String expectedJosa) {
        assertEquals(expectedJosa, JosaPicker.pick(word, JosaType.A_YA));
    }

    @Test
    @DisplayName("Number postpositions")
    void testNumberJosa() {
        // 1(일) ends in 'ㄹ', so '로'
        assertEquals("로", JosaPicker.pick("1", JosaType.EURO_RO));
        assertEquals("로", JosaPicker.pick("7", JosaType.EURO_RO));
        assertEquals("로", JosaPicker.pick("8", JosaType.EURO_RO));

        // 3(삼) ends in 'ㅁ', so '으로'
        assertEquals("으로", JosaPicker.pick("3", JosaType.EURO_RO));
        assertEquals("은", JosaPicker.pick("3", JosaType.EUN_NEUN));

        // 2(이) has no 받침, so '로', '는'
        assertEquals("로", JosaPicker.pick("2", JosaType.EURO_RO));
        assertEquals("는", JosaPicker.pick("2", JosaType.EUN_NEUN));
    }

    @Test
    @DisplayName("Template formatting test")
    void testTemplateFormatting() {
        assertEquals(
                "철수는 사과를 먹었다",
                JosaPicker.format("철수#{은/는} 사과#{을/를} 먹었다")
        );

        assertEquals(
                "영희가 책을 읽었다",
                JosaPicker.format("영희(이/가) 책(을/를) 읽었다")
        );

        assertEquals(
                "서울로 향했다",
                JosaPicker.format("서울#{으로/로} 향했다")
        );

        assertEquals(
                "부산으로 향했다",
                JosaPicker.format("부산#{으로/로} 향했다")
        );

        assertEquals(
                "철수는 사과를 먹었다",
                JosaPicker.format("#{철수:은/는} #{사과:을/를} 먹었다")
        );
    }
}
