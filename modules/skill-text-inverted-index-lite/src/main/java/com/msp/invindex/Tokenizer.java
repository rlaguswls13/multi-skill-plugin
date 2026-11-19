package com.msp.invindex;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Normalizes text, splits into alphanumeric tokens, and filters common stopwords.
 */
public final class Tokenizer {

    public static final Set<String> DEFAULT_STOP_WORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
            "by", "could", "did", "do", "does", "doing", "down", "during", "each", "few", "for", "from",
            "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him",
            "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself", "me", "more",
            "most", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other",
            "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "she", "should", "so",
            "some", "such", "than", "that", "the", "their", "theirs", "them", "themselves", "then",
            "there", "these", "they", "this", "those", "through", "to", "too", "under", "until", "up",
            "very", "was", "we", "were", "what", "when", "where", "which", "while", "who", "whom", "why",
            "with", "would", "you", "your", "yours", "yourself", "yourselves"
    );

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final Set<String> stopWords;
    private final boolean stopWordsEnabled;

    public Tokenizer() {
        this(DEFAULT_STOP_WORDS, true);
    }

    public Tokenizer(Set<String> stopWords, boolean stopWordsEnabled) {
        this.stopWords = stopWords != null ? Collections.unmodifiableSet(new HashSet<>(stopWords)) : Collections.emptySet();
        this.stopWordsEnabled = stopWordsEnabled;
    }

    public static Tokenizer standard() {
        return new Tokenizer();
    }

    public static Tokenizer withoutStopWords() {
        return new Tokenizer(Collections.emptySet(), false);
    }

    /**
     * Splits text into lowercase normalized tokens.
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (!stopWordsEnabled || !stopWords.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /**
     * Normalizes a single query term (lowercase and trimmed).
     */
    public String normalizeTerm(String term) {
        if (term == null) return "";
        return term.toLowerCase(Locale.ROOT).trim();
    }
}
