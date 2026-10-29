package com.msp.slug;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SlugGeneratorTest {

    @Test
    @DisplayName("Generate slug with default options for standard English title")
    void testStandardEnglish() {
        String slug = SlugGenerator.generate("Hello World 2026!");
        assertEquals("hello-world-2026", slug);
    }

    @Test
    @DisplayName("Punctuation, ampersand, and repeated symbols normalize cleanly")
    void testPunctuationAndAmpersand() {
        String slug = SlugGenerator.generate("Java 21 & Spring Boot: The Ultimate Guide?!");
        assertEquals("java-21-and-spring-boot-the-ultimate-guide", slug);
    }

    @Test
    @DisplayName("Latin accents are decomposed and transliterated (e.g., é -> e, ü -> u)")
    void testAccentsTransliteration() {
        String slug = SlugGenerator.generate("Café au Lait & Crème Brûlée Über München");
        assertEquals("cafe-au-lait-and-creme-brulee-uber-munchen", slug);
    }

    @Test
    @DisplayName("Special Latin ligatures (æ, œ, ß, ø) transliterate accurately")
    void testLigatures() {
        String slug = SlugGenerator.generate("Façade & Strauß & Encyclopædia");
        assertEquals("facade-and-strauss-and-encyclopaedia", slug);
    }

    @Test
    @DisplayName("Korean title is preserved when allowUnicode is enabled")
    void testKoreanWithUnicodeOption() {
        String title = "자바 21 신기능 & 마이크로서비스 가이드!";
        String slug = SlugGenerator.generate(title, SlugOptions.UNICODE);
        assertEquals("자바-21-신기능-and-마이크로서비스-가이드", slug);
    }

    @Test
    @DisplayName("Korean characters are stripped when allowUnicode is disabled (ASCII only)")
    void testKoreanStrippedInAsciiMode() {
        String title = "자바 21 Java 21 Guide!";
        String slug = SlugGenerator.generate(title, SlugOptions.DEFAULT);
        assertEquals("21-java-21-guide", slug);
    }

    @Test
    @DisplayName("Custom separator '_' is supported")
    void testCustomSeparator() {
        SlugOptions opts = SlugOptions.builder()
                .separator("_")
                .build();
        String slug = SlugGenerator.generate("Hello World From MSP", opts);
        assertEquals("hello_world_from_msp", slug);
    }

    @Test
    @DisplayName("Max length with word boundary truncation does not slice middle of a word")
    void testMaxLengthWithWordBoundary() {
        SlugOptions opts = SlugOptions.builder()
                .maxLength(20)
                .wordBoundaryTruncation(true)
                .build();

        // Length of "building-microservices-in-java" is 30.
        // First 20 chars: "building-microservic" -> boundary snaps back to "building"
        String slug = SlugGenerator.generate("Building Microservices in Java", opts);
        assertEquals("building", slug);
        assertTrue(slug.length() <= 20);
    }

    @Test
    @DisplayName("Max length without word boundary truncates strictly at character limit")
    void testMaxLengthWithoutWordBoundary() {
        SlugOptions opts = SlugOptions.builder()
                .maxLength(15)
                .wordBoundaryTruncation(false)
                .build();

        String slug = SlugGenerator.generate("Building Microservices in Java", opts);
        assertEquals("building-micros", slug);
        assertEquals(15, slug.length());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "---", "***"})
    @DisplayName("Null, blank, or all-symbol inputs return empty string")
    void testEmptyAndBlankInputs(String input) {
        String slug = SlugGenerator.generate(input);
        assertEquals("", slug);
    }

    @Test
    @DisplayName("Preserve case when lowercase option is disabled")
    void testPreserveCase() {
        SlugOptions opts = SlugOptions.builder()
                .lowercase(false)
                .build();
        String slug = SlugGenerator.generate("Modern Java21 Features", opts);
        assertEquals("Modern-Java21-Features", slug);
    }
}
