package com.msp.keyword;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Keyword extractor using Term Frequency (TF) and Stopword filtering for Korean and English text.
 * Strips punctuation, normalizes cases, and strips common Korean postpositions (조사).
 * Thread-safe and zero-dependency.
 */
public class KeywordExtractor {

    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^a-zA-Z0-9가-힣]+");
    private static final Pattern NUMERIC_ONLY = Pattern.compile("^\\d+$");
    private static final Pattern HANGUL_PATTERN = Pattern.compile("^[가-힣]+$");

    // Standard English Stopwords
    private static final Set<String> DEFAULT_ENGLISH_STOPWORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
            "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
            "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
            "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
            "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no",
            "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
            "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
            "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then",
            "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those",
            "through", "to", "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're",
            "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while",
            "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll",
            "you're", "you've", "your", "yours", "yourself", "yourselves"
    );

    // Standard Korean Stopwords
    private static final Set<String> DEFAULT_KOREAN_STOPWORDS = Set.of(
            "은", "는", "이", "가", "을", "를", "의", "에", "로", "으로", "와", "과", "등",
            "에서", "에게", "도", "만", "참", "잘", "곧", "더", "및", "즉", "약", "쯤", "몇",
            "그", "저", "이렇듯", "저렇듯", "그리고", "그러나", "그래서", "또한", "또는", "하고",
            "이며", "하며", "했다", "이다", "있다", "없다", "하는", "되는", "대한", "위한", "통해", "따라",
            "수", "것", "등등", "때문", "경우", "이러한", "저러한", "때", "모든", "어떤"
    );

    // Common Korean postpositions to strip (ordered by length descending)
    private static final List<String> KOREAN_PARTICLES = List.of(
            "에서는", "에서도", "에게서", "으로부터", "으로", "에서", "에게", "부터", "까지",
            "하고", "이나", "이란", "보다", "은", "는", "이", "가", "을", "를", "의", "에", "로", "와", "과", "도", "만"
    );

    private final Set<String> stopwords;

    /**
     * Constructs a KeywordExtractor with default English and Korean stopwords.
     */
    public KeywordExtractor() {
        this(Set.of());
    }

    /**
     * Constructs a KeywordExtractor with default stopwords combined with custom stopwords.
     *
     * @param customStopwords additional stopwords to ignore
     */
    public KeywordExtractor(Set<String> customStopwords) {
        Set<String> set = new HashSet<>(DEFAULT_ENGLISH_STOPWORDS);
        set.addAll(DEFAULT_KOREAN_STOPWORDS);
        if (customStopwords != null) {
            for (String sw : customStopwords) {
                if (sw != null && !sw.isBlank()) {
                    set.add(sw.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.stopwords = Collections.unmodifiableSet(set);
    }

    public static KeywordExtractor create() {
        return new KeywordExtractor();
    }

    /**
     * Returns a new KeywordExtractor instance with additional custom stopwords.
     *
     * @param customStopwords extra stopwords to ignore
     * @return new configured KeywordExtractor
     */
    public KeywordExtractor withStopwords(Set<String> customStopwords) {
        Set<String> merged = new HashSet<>(this.stopwords);
        if (customStopwords != null) {
            for (String sw : customStopwords) {
                if (sw != null && !sw.isBlank()) {
                    merged.add(sw.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return new KeywordExtractor(merged);
    }

    /**
     * Extracts top N keywords from the text, sorted descending by TF frequency and score.
     *
     * @param text input raw text
     * @param topN maximum number of keywords to return (must be > 0)
     * @return list of top KeywordScore records
     */
    public List<KeywordScore> extractTopKeywords(String text, int topN) {
        if (text == null || text.isBlank() || topN <= 0) {
            return List.of();
        }

        String[] tokens = TOKEN_SPLIT_PATTERN.split(text);
        Map<String, Integer> freqMap = new HashMap<>();
        int totalValidTokens = 0;

        for (String rawToken : tokens) {
            String word = normalizeWord(rawToken);
            if (isValidKeyword(word)) {
                freqMap.merge(word, 1, Integer::sum);
                totalValidTokens++;
            }
        }

        if (totalValidTokens == 0) {
            return List.of();
        }

        final int totalTokens = totalValidTokens;
        return freqMap.entrySet().stream()
                .map(e -> {
                    String word = e.getKey();
                    int count = e.getValue();
                    double tfScore = Math.round(((double) count / totalTokens) * 10000.0) / 10000.0;
                    return new KeywordScore(word, count, tfScore);
                })
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }

    private String normalizeWord(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        // If Hangul word, attempt particle stripping
        if (HANGUL_PATTERN.matcher(trimmed).matches()) {
            for (String particle : KOREAN_PARTICLES) {
                if (trimmed.endsWith(particle)) {
                    String stem = trimmed.substring(0, trimmed.length() - particle.length());
                    // Keep root stem if at least 2 characters long
                    if (stem.length() >= 2) {
                        return stem;
                    }
                }
            }
            return trimmed;
        }

        // Normalize English / alphanumeric to lowercase
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean isValidKeyword(String word) {
        if (word == null || word.length() < 2) {
            return false;
        }
        if (NUMERIC_ONLY.matcher(word).matches()) {
            return false;
        }
        return !stopwords.contains(word);
    }

    /**
     * Returns immutable set of all active stopwords.
     */
    public Set<String> stopwords() {
        return stopwords;
    }
}
