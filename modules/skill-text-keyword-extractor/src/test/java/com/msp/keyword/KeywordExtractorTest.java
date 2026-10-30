package com.msp.keyword;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KeywordExtractorTest {

    private final KeywordExtractor extractor = new KeywordExtractor();

    @Test
    @DisplayName("Extracts top keywords from English text and ignores stopwords")
    void testEnglishKeywords() {
        String text = """
                Java is a high-level, class-based, object-oriented programming language.
                Java is designed to have as few implementation dependencies as possible.
                Java developers love writing clean Java code.
                """;

        List<KeywordScore> keywords = extractor.extractTopKeywords(text, 5);

        assertFalse(keywords.isEmpty());
        assertEquals("java", keywords.getFirst().word());
        assertTrue(keywords.getFirst().count() >= 4);
        assertTrue(keywords.getFirst().score() > 0.0);

        // Ensure common stopwords like "is", "a", "as", "to" are excluded
        boolean hasStopwords = keywords.stream().anyMatch(k -> Set.of("is", "a", "as", "to").contains(k.word()));
        assertFalse(hasStopwords);
    }

    @Test
    @DisplayName("Extracts top keywords from Korean text and strips postpositions")
    void testKoreanKeywords() {
        String text = """
                인공지능은 현대 사회에서 매우 중요한 기술입니다.
                많은 기업들이 인공지능을 도입하고 있으며, 인공지능의 발전 속도는 매우 빠릅니다.
                인공지능 연구원들은 새로운 알고리즘을 개발합니다.
                """;

        List<KeywordScore> keywords = extractor.extractTopKeywords(text, 5);

        assertFalse(keywords.isEmpty());
        // "인공지능은", "인공지능을", "인공지능의" should all normalize to "인공지능"
        assertEquals("인공지능", keywords.getFirst().word());
        assertTrue(keywords.getFirst().count() >= 3);
    }

    @Test
    @DisplayName("Custom stopwords filtering via withStopwords")
    void testCustomStopwords() {
        String text = "Spring Boot microservice architecture and Spring Cloud framework";
        KeywordExtractor customExtractor = extractor.withStopwords(Set.of("spring", "boot"));

        List<KeywordScore> keywords = customExtractor.extractTopKeywords(text, 5);

        boolean containsSpring = keywords.stream().anyMatch(k -> k.word().equals("spring") || k.word().equals("boot"));
        assertFalse(containsSpring);
        assertTrue(keywords.stream().anyMatch(k -> k.word().equals("microservice")));
    }

    @Test
    @DisplayName("Handles empty or null text gracefully")
    void testEmptyAndNull() {
        assertTrue(extractor.extractTopKeywords(null, 5).isEmpty());
        assertTrue(extractor.extractTopKeywords("   ", 5).isEmpty());
        assertTrue(extractor.extractTopKeywords("the a an", 5).isEmpty());
        assertTrue(extractor.extractTopKeywords("Valid text", 0).isEmpty());
        assertTrue(extractor.extractTopKeywords("Valid text", -1).isEmpty());
    }

    @Test
    @DisplayName("Mixed English and Korean text extraction")
    void testMixedLanguage() {
        String text = "Docker 컨테이너 기술은 클라우드 환경에서 Docker 이미지 배포를 혁신했습니다.";

        List<KeywordScore> keywords = extractor.extractTopKeywords(text, 3);
        assertFalse(keywords.isEmpty());
        assertEquals("docker", keywords.getFirst().word());
        assertEquals(2, keywords.getFirst().count());
    }
}
