package com.msp.clausesplit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * High-performance, zero-dependency SQL lexer and clause splitter.
 * <p>
 * Accurately parses top-level SQL clauses while correctly ignoring keywords
 * nested within parentheses (subqueries), string literals, or comments.
 */
public final class SqlClauseSplitter {

    private SqlClauseSplitter() {
        // Utility class
    }

    /**
     * Splits the given SQL statement into top-level clauses.
     *
     * @param sql the SQL statement to split
     * @return the parsed {@link SqlSplitResult}
     * @throws IllegalArgumentException if sql is null or blank
     */
    public static SqlSplitResult split(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL statement cannot be null or blank");
        }

        List<ClauseMatch> matches = findTopLevelClauses(sql);
        if (matches.isEmpty()) {
            return new SqlSplitResult(sql, "", List.of());
        }

        String leadingPrefix = sql.substring(0, matches.get(0).start);
        List<SqlClause> clauses = new ArrayList<>(matches.size());

        for (int i = 0; i < matches.size(); i++) {
            ClauseMatch cur = matches.get(i);
            int bodyStart = cur.end;
            int bodyEnd = (i + 1 < matches.size()) ? matches.get(i + 1).start : sql.length();

            String rawBody = sql.substring(bodyStart, bodyEnd);
            // If this is the last clause, strip any trailing semicolon
            if (i == matches.size() - 1) {
                rawBody = stripTrailingSemicolon(rawBody);
            }

            clauses.add(new SqlClause(
                    cur.type,
                    cur.matchedKeyword,
                    rawBody,
                    cur.start,
                    bodyEnd
            ));
        }

        return new SqlSplitResult(sql, leadingPrefix, clauses);
    }

    /**
     * Convenience method to check if an SQL statement contains a top-level clause of the specified type.
     */
    public static boolean containsClause(String sql, ClauseType type) {
        if (sql == null || sql.isBlank() || type == null) {
            return false;
        }
        return split(sql).hasClause(type);
    }

    private static String stripTrailingSemicolon(String text) {
        int idx = text.length() - 1;
        while (idx >= 0 && Character.isWhitespace(text.charAt(idx))) {
            idx--;
        }
        if (idx >= 0 && text.charAt(idx) == ';') {
            return text.substring(0, idx);
        }
        return text;
    }

    private static List<ClauseMatch> findTopLevelClauses(String sql) {
        List<ClauseMatch> matches = new ArrayList<>();
        int len = sql.length();
        int parenDepth = 0;
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            // 1. Single-line comments: -- or #
            if ((c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') || c == '#') {
                i = skipUntilLineEnd(sql, i + (c == '#' ? 1 : 2));
                continue;
            }

            // 2. Block comments: /* ... */
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                i = skipUntilBlockCommentEnd(sql, i + 2);
                continue;
            }

            // 3. String literals: '...', "...", `...`
            if (c == '\'' || c == '"' || c == '`') {
                i = skipStringLiteral(sql, i, c);
                continue;
            }

            // 4. Bracketed identifiers: [...] (SQL Server style)
            if (c == '[') {
                i = skipBracketIdentifier(sql, i);
                continue;
            }

            // 5. Parenthesis depth tracking
            if (c == '(') {
                parenDepth++;
                i++;
                continue;
            }
            if (c == ')') {
                if (parenDepth > 0) {
                    parenDepth--;
                }
                i++;
                continue;
            }

            // 6. Check for keywords ONLY at top level (parenDepth == 0)
            if (parenDepth == 0 && isWordBoundary(sql, i - 1)) {
                ClauseMatch match = tryMatchClauseKeyword(sql, i);
                if (match != null) {
                    matches.add(match);
                    i = match.end;
                    continue;
                }
            }

            i++;
        }

        return matches;
    }

    private static ClauseMatch tryMatchClauseKeyword(String sql, int start) {
        int len = sql.length();

        // Check multi-word clauses first
        ClauseMatch match = checkMultiWord(sql, start, "UNION", "ALL", ClauseType.UNION_ALL);
        if (match != null) return match;

        match = checkMultiWord(sql, start, "GROUP", "BY", ClauseType.GROUP_BY);
        if (match != null) return match;

        match = checkMultiWord(sql, start, "ORDER", "BY", ClauseType.ORDER_BY);
        if (match != null) return match;

        match = checkMultiWord(sql, start, "INSERT", "INTO", ClauseType.INSERT_INTO);
        if (match != null) return match;

        match = checkMultiWord(sql, start, "DELETE", "FROM", ClauseType.DELETE_FROM);
        if (match != null) return match;

        // Check single-word clauses
        match = checkSingleWord(sql, start, "SELECT", ClauseType.SELECT);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "FROM", ClauseType.FROM);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "WHERE", ClauseType.WHERE);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "HAVING", ClauseType.HAVING);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "LIMIT", ClauseType.LIMIT);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "OFFSET", ClauseType.OFFSET);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "UNION", ClauseType.UNION);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "VALUES", ClauseType.VALUES);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "UPDATE", ClauseType.UPDATE);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "SET", ClauseType.SET);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "WITH", ClauseType.WITH);
        if (match != null) return match;

        match = checkSingleWord(sql, start, "RETURNING", ClauseType.RETURNING);
        if (match != null) return match;

        return null;
    }

    private static ClauseMatch checkSingleWord(String sql, int start, String keyword, ClauseType type) {
        int kwLen = keyword.length();
        int len = sql.length();
        if (start + kwLen > len) {
            return null;
        }

        if (sql.regionMatches(true, start, keyword, 0, kwLen)) {
            int after = start + kwLen;
            if (after >= len || isWordBoundary(sql, after)) {
                return new ClauseMatch(type, start, after, sql.substring(start, after));
            }
        }
        return null;
    }

    private static ClauseMatch checkMultiWord(String sql, int start, String word1, String word2, ClauseType type) {
        int len = sql.length();
        int w1Len = word1.length();

        if (start + w1Len > len || !sql.regionMatches(true, start, word1, 0, w1Len)) {
            return null;
        }

        int idx = start + w1Len;
        if (idx >= len || !Character.isWhitespace(sql.charAt(idx))) {
            return null;
        }

        // Skip whitespace between words
        while (idx < len && Character.isWhitespace(sql.charAt(idx))) {
            idx++;
        }

        int w2Len = word2.length();
        if (idx + w2Len > len || !sql.regionMatches(true, idx, word2, 0, w2Len)) {
            return null;
        }

        int after = idx + w2Len;
        if (after >= len || isWordBoundary(sql, after)) {
            return new ClauseMatch(type, start, after, sql.substring(start, after));
        }

        return null;
    }

    private static boolean isWordBoundary(String sql, int index) {
        if (index < 0 || index >= sql.length()) {
            return true;
        }
        char c = sql.charAt(index);
        return Character.isWhitespace(c)
                || c == '(' || c == ')' || c == ',' || c == ';' || c == '-' || c == '/'
                || !Character.isLetterOrDigit(c) && c != '_';
    }

    private static int skipUntilLineEnd(String sql, int start) {
        int len = sql.length();
        while (start < len) {
            char c = sql.charAt(start);
            if (c == '\n' || c == '\r') {
                return start + 1;
            }
            start++;
        }
        return len;
    }

    private static int skipUntilBlockCommentEnd(String sql, int start) {
        int len = sql.length();
        while (start < len - 1) {
            if (sql.charAt(start) == '*' && sql.charAt(start + 1) == '/') {
                return start + 2;
            }
            start++;
        }
        return len;
    }

    private static int skipStringLiteral(String sql, int start, char quoteChar) {
        int len = sql.length();
        int i = start + 1;
        while (i < len) {
            char c = sql.charAt(i);
            // Escaped quote: '' or "" or \'
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quoteChar) {
                if (i + 1 < len && sql.charAt(i + 1) == quoteChar) {
                    i += 2; // Escaped like ''
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return len;
    }

    private static int skipBracketIdentifier(String sql, int start) {
        int len = sql.length();
        int i = start + 1;
        while (i < len) {
            if (sql.charAt(i) == ']') {
                return i + 1;
            }
            i++;
        }
        return len;
    }

    private record ClauseMatch(ClauseType type, int start, int end, String matchedKeyword) {
    }
}
