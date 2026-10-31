package com.msp.bm25;

import java.util.List;
import java.util.Objects;

/**
 * Immutable representation of a document in the BM25 corpus.
 *
 * @param <T>    the type of document identifier
 * @param id     unique document identifier
 * @param tokens list of tokens/terms contained in the document
 */
public record Document<T>(T id, List<String> tokens) {

    public Document {
        Objects.requireNonNull(id, "Document id must not be null");
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }

    /**
     * Factory method creating a Document instance with a list of tokens.
     */
    public static <T> Document<T> of(T id, List<String> tokens) {
        return new Document<>(id, tokens);
    }

    /**
     * Factory method creating a Document instance with variable tokens.
     */
    public static <T> Document<T> of(T id, String... tokens) {
        return new Document<>(id, tokens == null ? List.of() : List.of(tokens));
    }

    /**
     * Returns the token length of this document.
     */
    public int length() {
        return tokens.size();
    }
}
