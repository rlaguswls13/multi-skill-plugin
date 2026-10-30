package com.msp.fuzzysearch;

import java.util.*;
import java.util.function.Function;

/**
 * 외부 검색엔진(Elasticsearch 등) 없이 인메모리에서
 * Levenshtein 편집 거리 및 부분 문자열 유사도를 기반으로
 * 오타 교정 및 유사 검색어 순위를 산출하는 퍼지 검색기.
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {}

    /**
     * 두 문자열 간의 Levenshtein 편집 거리를 계산합니다.
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return (s1 == null && s2 == null) ? 0 : Math.max(s1 != null ? s1.length() : 0, s2 != null ? s2.length() : 0);
        }

        int len1 = s1.length();
        int len2 = s2.length();
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, len2 + 1);
        }
        return prev[len2];
    }

    /**
     * 0.0 ~ 1.0 범위의 유사도(Similarity)를 계산합니다. (1.0 = 완전 일치)
     */
    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        int dist = levenshteinDistance(s1, s2);
        return 1.0 - ((double) dist / maxLen);
    }

    /**
     * 컬렉션 내 객체들의 텍스트 필드를 대상으로 질의어와 비교하여 minScore 이상인 항목을 순위별로 정렬 반환합니다.
     */
    public static <T> List<FuzzyMatchResult<T>> search(
            String query,
            Collection<T> items,
            Function<T, String> textExtractor,
            double minScore,
            int limit
    ) {
        if (query == null || query.isBlank() || items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        String q = query.trim().toLowerCase(Locale.ROOT);
        List<FuzzyMatchResult<T>> results = new ArrayList<>();

        for (T item : items) {
            String text = textExtractor.apply(item);
            if (text == null) continue;
            String t = text.trim().toLowerCase(Locale.ROOT);

            double score;
            int dist = levenshteinDistance(q, t);

            if (t.contains(q)) {
                // Exact substring match bonus
                score = 0.85 + (0.15 * ((double) q.length() / t.length()));
            } else {
                score = similarity(q, t);
            }

            if (score >= minScore) {
                results.add(new FuzzyMatchResult<>(item, text, score, dist));
            }
        }

        Collections.sort(results);
        if (results.size() > limit && limit > 0) {
            return results.subList(0, limit);
        }
        return results;
    }
}
