package com.msp.autocomplete;

import java.util.Objects;

/**
 * An autocomplete recommendation entry containing the matching word text and its ranking score.
 * Natural ordering sorts items descending by score, breaking ties by ascending text order.
 *
 * @param text  the matching word or phrase
 * @param score the numerical weight/frequency score
 */
public record AutocompleteItem(String text, double score) implements Comparable<AutocompleteItem> {

    public AutocompleteItem {
        Objects.requireNonNull(text, "text must not be null");
    }

    @Override
    public int compareTo(AutocompleteItem other) {
        int cmp = Double.compare(other.score, this.score); // descending by score
        if (cmp != 0) {
            return cmp;
        }
        return this.text.compareTo(other.text); // ascending by text
    }
}
