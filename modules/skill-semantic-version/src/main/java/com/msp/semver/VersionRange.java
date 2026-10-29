package com.msp.semver;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SemVer range parser and evaluator supporting npm/Cargo-style range expressions:
 * - Caret ranges (e.g. {@code ^1.2.3}, {@code ^0.2.3}, {@code ^0.0.3})
 * - Tilde ranges (e.g. {@code ~1.2.3}, {@code ~1.2})
 * - Comparison operators ({@code >=}, {@code <=}, {@code >}, {@code <}, {@code =})
 * - Hyphen ranges (e.g. {@code 1.2.3 - 2.3.4})
 * - Wildcards (e.g. {@code *}, {@code 1.x}, {@code 1.2.*})
 * - Space-separated conjunctions (AND, e.g. {@code >=1.0.0 <2.0.0})
 * - Pipe disjunctions (OR, e.g. {@code ^1.0.0 || ^2.0.0})
 */
public final class VersionRange {

    private final String expression;
    private final List<List<Predicate<SemVer>>> disjunctions;

    private VersionRange(String expression, List<List<Predicate<SemVer>>> disjunctions) {
        this.expression = expression;
        this.disjunctions = disjunctions;
    }

    /**
     * Parses a version range expression.
     */
    public static VersionRange parse(String expression) {
        Objects.requireNonNull(expression, "range expression must not be null");
        String trimmed = expression.trim();
        if (trimmed.isEmpty() || trimmed.equals("*")) {
            return new VersionRange(expression, List.of(List.of(v -> true)));
        }

        // Split by OR (||)
        String[] orClauses = trimmed.split("\\|\\|");
        List<List<Predicate<SemVer>>> disjunctionList = new ArrayList<>();

        for (String orClause : orClauses) {
            String clause = orClause.trim();
            if (clause.isEmpty()) {
                continue;
            }
            List<Predicate<SemVer>> conjunctionList = parseConjunction(clause);
            disjunctionList.add(conjunctionList);
        }

        return new VersionRange(expression, disjunctionList);
    }

    /**
     * Static helper evaluating if a SemVer satisfies a range expression.
     */
    public static boolean satisfies(String rangeExpression, SemVer version) {
        return parse(rangeExpression).isSatisfiedBy(version);
    }

    /**
     * Static helper evaluating if a version string satisfies a range expression.
     */
    public static boolean satisfies(String rangeExpression, String versionString) {
        return parse(rangeExpression).isSatisfiedBy(SemVer.parse(versionString));
    }

    /**
     * Evaluates if the given SemVer satisfies this range.
     */
    public boolean isSatisfiedBy(SemVer version) {
        if (version == null) {
            return false;
        }
        for (List<Predicate<SemVer>> conjunction : disjunctions) {
            boolean allMatch = true;
            for (Predicate<SemVer> predicate : conjunction) {
                if (!predicate.test(version)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates if the given version string satisfies this range.
     */
    public boolean isSatisfiedBy(String versionString) {
        return isSatisfiedBy(SemVer.parse(versionString));
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }

    private static List<Predicate<SemVer>> parseConjunction(String clause) {
        List<Predicate<SemVer>> predicates = new ArrayList<>();

        // Check for hyphen range: "1.2.3 - 2.3.4"
        if (clause.contains(" - ")) {
            String[] parts = clause.split(" - ");
            if (parts.length == 2) {
                SemVer min = SemVer.parseLenient(parts[0].trim());
                SemVer max = SemVer.parseLenient(parts[1].trim());
                predicates.add(v -> v.compareTo(min) >= 0);
                predicates.add(v -> v.compareTo(max) <= 0);
                return predicates;
            }
        }

        // Split by whitespace
        String[] tokens = clause.split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            predicates.addAll(parseToken(token));
        }

        return predicates;
    }

    private static List<Predicate<SemVer>> parseToken(String token) {
        List<Predicate<SemVer>> result = new ArrayList<>();

        // Wildcards: *, x, X
        if (token.equals("*") || token.equalsIgnoreCase("x")) {
            result.add(v -> true);
            return result;
        }

        // Caret range: ^1.2.3
        if (token.startsWith("^")) {
            String rawVer = token.substring(1);
            SemVer min = SemVer.parseLenient(rawVer);
            SemVer max;
            if (min.getMajor() > 0) {
                max = SemVer.of(min.getMajor() + 1, 0, 0);
            } else if (min.getMinor() > 0) {
                max = SemVer.of(0, min.getMinor() + 1, 0);
            } else {
                max = SemVer.of(0, 0, min.getPatch() + 1);
            }
            result.add(v -> v.compareTo(min) >= 0 && v.compareTo(max) < 0);
            return result;
        }

        // Tilde range: ~1.2.3, ~1.2, ~1
        if (token.startsWith("~")) {
            String rawVer = token.substring(1);
            String[] parts = rawVer.split("\\.");
            SemVer min = SemVer.parseLenient(rawVer);
            SemVer max;
            if (parts.length == 1) {
                // ~1 -> >=1.0.0 <2.0.0
                max = SemVer.of(min.getMajor() + 1, 0, 0);
            } else {
                // ~1.2 or ~1.2.3 -> >=1.2.x <1.3.0
                max = SemVer.of(min.getMajor(), min.getMinor() + 1, 0);
            }
            result.add(v -> v.compareTo(min) >= 0 && v.compareTo(max) < 0);
            return result;
        }

        // Comparison operators: >=, <=, >, <, =
        if (token.startsWith(">=")) {
            SemVer target = SemVer.parseLenient(token.substring(2));
            result.add(v -> v.compareTo(target) >= 0);
            return result;
        } else if (token.startsWith("<=")) {
            SemVer target = SemVer.parseLenient(token.substring(2));
            result.add(v -> v.compareTo(target) <= 0);
            return result;
        } else if (token.startsWith(">")) {
            SemVer target = SemVer.parseLenient(token.substring(1));
            result.add(v -> v.compareTo(target) > 0);
            return result;
        } else if (token.startsWith("<")) {
            SemVer target = SemVer.parseLenient(token.substring(1));
            result.add(v -> v.compareTo(target) < 0);
            return result;
        } else if (token.startsWith("=")) {
            SemVer target = SemVer.parseLenient(token.substring(1));
            result.add(v -> v.isEquivalentTo(target));
            return result;
        }

        // Wildcard patterns like 1.x or 1.2.*
        if (token.contains("*") || token.toLowerCase().contains("x")) {
            String[] parts = token.split("\\.");
            if (parts.length >= 2 && (parts[1].equals("*") || parts[1].equalsIgnoreCase("x"))) {
                int maj = Integer.parseInt(parts[0]);
                SemVer min = SemVer.of(maj, 0, 0);
                SemVer max = SemVer.of(maj + 1, 0, 0);
                result.add(v -> v.compareTo(min) >= 0 && v.compareTo(max) < 0);
                return result;
            } else if (parts.length >= 3 && (parts[2].equals("*") || parts[2].equalsIgnoreCase("x"))) {
                int maj = Integer.parseInt(parts[0]);
                int minVal = Integer.parseInt(parts[1]);
                SemVer min = SemVer.of(maj, minVal, 0);
                SemVer max = SemVer.of(maj, minVal + 1, 0);
                result.add(v -> v.compareTo(min) >= 0 && v.compareTo(max) < 0);
                return result;
            }
        }

        // Exact version match
        SemVer target = SemVer.parseLenient(token);
        result.add(v -> v.isEquivalentTo(target));
        return result;
    }
}
