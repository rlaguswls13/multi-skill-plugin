package com.msp.transducer;

import java.util.Arrays;
import java.util.Objects;

/**
 * Non-deterministic Levenshtein Automaton state tracker for query string W and edit distance K.
 */
public final class LevenshteinAutomaton {

    private final String query;
    private final int maxDistance;

    public record State(int[] distances) {
        public State {
            distances = distances.clone();
        }

        public int minDistance() {
            int min = Integer.MAX_VALUE;
            for (int d : distances) {
                if (d < min) {
                    min = d;
                }
            }
            return min;
        }

        public int targetDistance() {
            return distances[distances.length - 1];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof State state)) return false;
            return Arrays.equals(distances, state.distances);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(distances);
        }

        @Override
        public String toString() {
            return Arrays.toString(distances);
        }
    }

    public LevenshteinAutomaton(String query, int maxDistance) {
        this.query = Objects.requireNonNull(query, "query cannot be null");
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance cannot be negative");
        }
        this.maxDistance = maxDistance;
    }

    public String getQuery() {
        return query;
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public State start() {
        int m = query.length();
        int[] d = new int[m + 1];
        for (int i = 0; i <= m; i++) {
            d[i] = i;
        }
        return new State(d);
    }

    public State step(State current, char c) {
        int[] d = current.distances;
        int m = query.length();
        int[] next = new int[m + 1];

        next[0] = d[0] + 1;

        for (int i = 1; i <= m; i++) {
            int cost = (query.charAt(i - 1) == c) ? 0 : 1;
            int deletion = d[i] + 1;
            int insertion = next[i - 1] + 1;
            int substitution = d[i - 1] + cost;

            next[i] = Math.min(deletion, Math.min(insertion, substitution));
        }

        return new State(next);
    }

    public boolean canMatch(State state) {
        return state.minDistance() <= maxDistance;
    }

    public boolean isAccepting(State state) {
        return state.targetDistance() <= maxDistance;
    }

    public int getDistance(State state) {
        return state.targetDistance();
    }
}
