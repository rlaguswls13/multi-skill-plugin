package com.msp.ahocorasick;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;

/**
 * High-performance Aho-Corasick multi-pattern Trie string search automaton.
 * <p>
 * Finds all occurrences of $K$ keywords simultaneously in a text of length $N$
 * in optimal $O(N + M)$ time using BFS-constructed failure and dictionary suffix links.
 */
public final class AhoCorasick {

    private final TrieNode root;
    private final boolean caseInsensitive;
    private final int patternCount;

    private record PatternEntry(String originalKeyword, Object payload) {}

    private static final class TrieNode {
        final Map<Character, TrieNode> transitions = new HashMap<>();
        TrieNode failure;
        final List<PatternEntry> outputs = new ArrayList<>(1);
    }

    private AhoCorasick(TrieNode root, boolean caseInsensitive, int patternCount) {
        this.root = root;
        this.caseInsensitive = caseInsensitive;
        this.patternCount = patternCount;
    }

    /**
     * Creates a new Builder for constructing an AhoCorasick automaton.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an AhoCorasick instance from a collection of keywords with default settings.
     */
    public static AhoCorasick from(Collection<String> keywords) {
        Builder b = builder();
        b.addKeywords(keywords);
        return b.build();
    }

    /**
     * Total number of patterns compiled into this automaton.
     */
    public int patternCount() {
        return patternCount;
    }

    /**
     * Whether search is case-insensitive.
     */
    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    /**
     * Finds all matching pattern occurrences (including overlapping matches).
     *
     * @param text input text to search
     * @return list of pattern matches in encounter order
     */
    public List<PatternMatch> findAll(CharSequence text) {
        if (text == null || text.isEmpty() || patternCount == 0) {
            return List.of();
        }

        List<PatternMatch> matches = new ArrayList<>();
        TrieNode curr = root;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (caseInsensitive) {
                ch = Character.toLowerCase(ch);
            }

            while (curr != root && !curr.transitions.containsKey(ch)) {
                curr = curr.failure;
            }

            curr = curr.transitions.getOrDefault(ch, root);

            if (!curr.outputs.isEmpty()) {
                for (PatternEntry entry : curr.outputs) {
                    int patternLen = entry.originalKeyword.length();
                    int start = i - patternLen + 1;
                    int end = i + 1;
                    matches.add(new PatternMatch(entry.originalKeyword, start, end, entry.payload));
                }
            }
        }

        return matches;
    }

    /**
     * Finds all non-overlapping matches, prioritizing earliest start and longest match.
     *
     * @param text input text
     * @return non-overlapping match list
     */
    public List<PatternMatch> findNonOverlapping(CharSequence text) {
        List<PatternMatch> all = findAll(text);
        if (all.isEmpty()) {
            return List.of();
        }

        // Sort by start ascending, then length descending (longest first)
        List<PatternMatch> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingInt(PatternMatch::start)
                .thenComparing((a, b) -> Integer.compare(b.length(), a.length())));

        List<PatternMatch> result = new ArrayList<>();
        int lastEnd = -1;

        for (PatternMatch match : sorted) {
            if (match.start() >= lastEnd) {
                result.add(match);
                lastEnd = match.end();
            }
        }

        return result;
    }

    /**
     * Returns true as soon as the first matching keyword is encountered (early exit).
     */
    public boolean containsAny(CharSequence text) {
        if (text == null || text.isEmpty() || patternCount == 0) {
            return false;
        }

        TrieNode curr = root;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (caseInsensitive) {
                ch = Character.toLowerCase(ch);
            }

            while (curr != root && !curr.transitions.containsKey(ch)) {
                curr = curr.failure;
            }

            curr = curr.transitions.getOrDefault(ch, root);

            if (!curr.outputs.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the first pattern match encountered.
     */
    public Optional<PatternMatch> findFirst(CharSequence text) {
        List<PatternMatch> matches = findAll(text);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    /**
     * Masks all matched keywords with a specified masking character (e.g. '*').
     * Accurately handles overlapping and adjacent matches.
     *
     * @param text     source text
     * @param maskChar masking character
     * @return masked string
     */
    public String maskMatches(CharSequence text, char maskChar) {
        if (text == null) {
            return null;
        }
        int len = text.length();
        if (len == 0) {
            return "";
        }

        List<PatternMatch> matches = findAll(text);
        if (matches.isEmpty()) {
            return text.toString();
        }

        boolean[] masked = new boolean[len];
        for (PatternMatch match : matches) {
            int start = Math.max(0, match.start());
            int end = Math.min(len, match.end());
            for (int i = start; i < end; i++) {
                masked[i] = true;
            }
        }

        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(masked[i] ? maskChar : text.charAt(i));
        }
        return sb.toString();
    }

    /**
     * Replaces non-overlapping matched patterns using a custom replacer function.
     *
     * @param text     source text
     * @param replacer function converting match into replacement string
     * @return modified text
     */
    public String replaceMatches(CharSequence text, Function<PatternMatch, String> replacer) {
        Objects.requireNonNull(replacer, "replacer must not be null");
        if (text == null) {
            return null;
        }
        int len = text.length();
        if (len == 0) {
            return "";
        }

        List<PatternMatch> matches = findNonOverlapping(text);
        if (matches.isEmpty()) {
            return text.toString();
        }

        StringBuilder sb = new StringBuilder(len + 16);
        int cursor = 0;

        for (PatternMatch match : matches) {
            sb.append(text, cursor, match.start());
            String replacement = replacer.apply(match);
            if (replacement != null) {
                sb.append(replacement);
            }
            cursor = match.end();
        }

        if (cursor < len) {
            sb.append(text, cursor, len);
        }

        return sb.toString();
    }

    /**
     * Builder for {@link AhoCorasick}.
     */
    public static final class Builder {
        private final Map<String, Object> keywords = new HashMap<>();
        private boolean caseInsensitive = false;

        private Builder() {}

        /**
         * Adds a single search keyword.
         */
        public Builder addKeyword(String keyword) {
            return addKeyword(keyword, null);
        }

        /**
         * Adds a search keyword with an associated payload object.
         */
        public Builder addKeyword(String keyword, Object payload) {
            Objects.requireNonNull(keyword, "keyword must not be null");
            if (keyword.isEmpty()) {
                throw new IllegalArgumentException("keyword must not be empty");
            }
            keywords.put(keyword, payload);
            return this;
        }

        /**
         * Adds multiple keywords.
         */
        public Builder addKeywords(Collection<String> list) {
            Objects.requireNonNull(list, "keywords collection must not be null");
            for (String kw : list) {
                addKeyword(kw);
            }
            return this;
        }

        /**
         * Configures whether matching is case-insensitive.
         */
        public Builder caseInsensitive(boolean caseInsensitive) {
            this.caseInsensitive = caseInsensitive;
            return this;
        }

        /**
         * Compiles the Trie, failure links, and dictionary suffix links.
         */
        public AhoCorasick build() {
            TrieNode root = new TrieNode();

            // 1. Build Trie
            for (Map.Entry<String, Object> entry : keywords.entrySet()) {
                String original = entry.getKey();
                String key = caseInsensitive ? original.toLowerCase() : original;
                TrieNode curr = root;

                for (int i = 0; i < key.length(); i++) {
                    char ch = key.charAt(i);
                    curr = curr.transitions.computeIfAbsent(ch, k -> new TrieNode());
                }
                curr.outputs.add(new PatternEntry(original, entry.getValue()));
            }

            // 2. Build BFS Failure Links and Output Links
            Queue<TrieNode> queue = new ArrayDeque<>();
            for (TrieNode child : root.transitions.values()) {
                child.failure = root;
                queue.add(child);
            }

            while (!queue.isEmpty()) {
                TrieNode curr = queue.poll();

                for (Map.Entry<Character, TrieNode> transition : curr.transitions.entrySet()) {
                    char ch = transition.getKey();
                    TrieNode child = transition.getValue();

                    TrieNode failure = curr.failure;
                    while (failure != null && !failure.transitions.containsKey(ch)) {
                        failure = failure.failure;
                    }

                    child.failure = (failure != null) ? failure.transitions.get(ch) : root;
                    child.outputs.addAll(child.failure.outputs);
                    queue.add(child);
                }
            }

            return new AhoCorasick(root, caseInsensitive, keywords.size());
        }
    }
}
