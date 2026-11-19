package com.msp.transducer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Levenshtein Transducer intersecting a DictionaryTrie with a Levenshtein Automaton
 * for ultra-fast spell checking and fuzzy matching within edit distance K.
 */
public final class LevenshteinTransducer {

    public static List<FuzzyMatch> search(DictionaryTrie trie, String query, int maxDistance) {
        return search(trie, query, maxDistance, Integer.MAX_VALUE);
    }

    public static List<FuzzyMatch> search(DictionaryTrie trie, String query, int maxDistance, int maxResults) {
        Objects.requireNonNull(trie, "trie cannot be null");
        Objects.requireNonNull(query, "query cannot be null");
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance cannot be negative");
        }
        if (maxResults <= 0) {
            return List.of();
        }

        LevenshteinAutomaton automaton = new LevenshteinAutomaton(query, maxDistance);
        List<FuzzyMatch> results = new ArrayList<>();

        traverse(trie.getRoot(), automaton.start(), automaton, results);

        Collections.sort(results);

        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        }
        return results;
    }

    public static Optional<FuzzyMatch> findClosest(DictionaryTrie trie, String query, int maxDistance) {
        List<FuzzyMatch> matches = search(trie, query, maxDistance, 1);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    private static void traverse(
            DictionaryTrie.Node node,
            LevenshteinAutomaton.State state,
            LevenshteinAutomaton automaton,
            List<FuzzyMatch> results
    ) {
        if (node.isTerminal() && automaton.isAccepting(state)) {
            results.add(new FuzzyMatch(node.getWord(), automaton.getDistance(state)));
        }

        for (var entry : node.getChildren().entrySet()) {
            char ch = entry.getKey();
            DictionaryTrie.Node child = entry.getValue();

            LevenshteinAutomaton.State nextState = automaton.step(state, ch);
            if (automaton.canMatch(nextState)) {
                traverse(child, nextState, automaton, results);
            }
            // else: Entire branch is pruned!
        }
    }

    /**
     * Compute standard dynamic programming Levenshtein edit distance between two strings.
     */
    public static int computeDistance(String s1, String s2) {
        Objects.requireNonNull(s1, "s1 cannot be null");
        Objects.requireNonNull(s2, "s2 cannot be null");

        int m = s1.length();
        int n = s2.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(prev[j] + 1, Math.min(curr[j - 1] + 1, prev[j - 1] + cost));
            }
            System.arraycopy(curr, 0, prev, 0, n + 1);
        }

        return prev[n];
    }
}
