package com.msp.keyword;

/**
 * Record representing an extracted keyword and its frequency score.
 *
 * @param word  the extracted keyword string
 * @param count raw term frequency count
 * @param score normalized term frequency score
 */
public record KeywordScore(String word, int count, double score) implements Comparable<KeywordScore> {

    @Override
    public int compareTo(KeywordScore other) {
        int cmp = Double.compare(other.score, this.score);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(other.count, this.count);
        if (cmp != 0) {
            return cmp;
        }
        return this.word.compareTo(other.word);
    }
}
