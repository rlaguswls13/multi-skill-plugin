package com.msp.ngram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NgramTokenizerTest {

    @Test
    @DisplayName("문자 N-gram 분해 검증 (1-gram, 2-gram, 3-gram)")
    void testCharacterNgrams() {
        List<String> unigrams = NgramTokenizer.characterNgrams("cat", 1);
        assertEquals(List.of("c", "a", "t"), unigrams);

        List<String> bigrams = NgramTokenizer.characterNgrams("hello", 2);
        assertEquals(List.of("he", "el", "ll", "lo"), bigrams);

        List<String> trigrams = NgramTokenizer.characterNgrams("banana", 3);
        assertEquals(List.of("ban", "ana", "nan", "ana"), trigrams);

        // 경계 조건
        assertTrue(NgramTokenizer.characterNgrams("hi", 3).isEmpty());
        assertTrue(NgramTokenizer.characterNgrams("", 2).isEmpty());
        assertTrue(NgramTokenizer.characterNgrams(null, 2).isEmpty());
        assertTrue(NgramTokenizer.characterNgrams("test", 0).isEmpty());
    }

    @Test
    @DisplayName("단어 N-gram 분해 검증")
    void testWordNgrams() {
        String sentence = "the quick brown fox jumps";
        List<String> bigrams = NgramTokenizer.wordNgrams(sentence, 2);
        assertEquals(List.of("the quick", "quick brown", "brown fox", "fox jumps"), bigrams);

        List<String> trigrams = NgramTokenizer.wordNgrams(sentence, 3);
        assertEquals(List.of("the quick brown", "quick brown fox", "brown fox jumps"), trigrams);

        // 공백 정규화
        List<String> spaced = NgramTokenizer.wordNgrams("  hello   world  java  ", 2);
        assertEquals(List.of("hello world", "world java"), spaced);

        // 경계 조건
        assertTrue(NgramTokenizer.wordNgrams("one", 2).isEmpty());
        assertTrue(NgramTokenizer.wordNgrams("   ", 1).isEmpty());
        assertTrue(NgramTokenizer.wordNgrams(null, 1).isEmpty());
    }

    @Test
    @DisplayName("문자 기반 Jaccard 유사도 계측 검증")
    void testJaccardSimilarity() {
        // 동일 문자열
        assertEquals(1.0, NgramTokenizer.jaccardSimilarity("night", "night", 2), 0.0001);
        assertEquals(1.0, NgramTokenizer.jaccardSimilarity("", "", 2), 0.0001);
        assertEquals(1.0, NgramTokenizer.jaccardSimilarity(null, null, 2), 0.0001);

        // null 비교
        assertEquals(0.0, NgramTokenizer.jaccardSimilarity("night", null, 2), 0.0001);

        // "night" (ni, ig, gh, ht) vs "nacht" (na, ac, ch, ht)
        // 교집합: {ht} (1개)
        // 합집합: {ni, ig, gh, ht, na, ac, ch} (7개)
        // 1 / 7 ≈ 0.142857
        double sim = NgramTokenizer.jaccardSimilarity("night", "nacht", 2);
        assertEquals(1.0 / 7.0, sim, 0.0001);

        // 완전히 다른 문자열
        assertEquals(0.0, NgramTokenizer.jaccardSimilarity("abc", "xyz", 2), 0.0001);
    }

    @Test
    @DisplayName("문자 기반 코사인 유사도 계측 검증")
    void testCosineSimilarity() {
        // 동일 문자열
        assertEquals(1.0, NgramTokenizer.cosineSimilarity("spring", "spring", 2), 0.0001);

        // 완전히 다른 문자열
        assertEquals(0.0, NgramTokenizer.cosineSimilarity("abc", "xyz", 2), 0.0001);

        // 부분 유사도 검증
        double sim = NgramTokenizer.cosineSimilarity("hello", "help", 2);
        // "hello": {he:1, el:1, ll:1, lo:1} -> norm = sqrt(4) = 2.0
        // "help":  {he:1, el:1, lp:1} -> norm = sqrt(3) = 1.73205
        // dotProduct: he(1*1) + el(1*1) = 2
        // cosine = 2 / (2 * sqrt(3)) = 1 / sqrt(3) ≈ 0.57735
        assertEquals(1.0 / Math.sqrt(3), sim, 0.0001);
    }

    @Test
    @DisplayName("단어 기반 Jaccard 및 코사인 유사도 검증")
    void testWordSimilarities() {
        String text1 = "machine learning models";
        String text2 = "deep learning models";

        // 단어 1-gram Jaccard
        // text1: {machine, learning, models}
        // text2: {deep, learning, models}
        // 교집합: {learning, models} (2개)
        // 합집합: {machine, deep, learning, models} (4개)
        // 2 / 4 = 0.5
        double jaccard = NgramTokenizer.wordJaccardSimilarity(text1, text2, 1);
        assertEquals(0.5, jaccard, 0.0001);

        // 단어 1-gram Cosine
        // dot = 2, norm1 = sqrt(3), norm2 = sqrt(3) -> 2 / 3 ≈ 0.6666
        double cosine = NgramTokenizer.wordCosineSimilarity(text1, text2, 1);
        assertEquals(2.0 / 3.0, cosine, 0.0001);

        // 단어 2-gram Jaccard
        // text1 2-grams: ["machine learning", "learning models"]
        // text2 2-grams: ["deep learning", "learning models"]
        // 교집합: 1, 합집합: 3 -> 1 / 3
        assertEquals(1.0 / 3.0, NgramTokenizer.wordJaccardSimilarity(text1, text2, 2), 0.0001);
    }
}
