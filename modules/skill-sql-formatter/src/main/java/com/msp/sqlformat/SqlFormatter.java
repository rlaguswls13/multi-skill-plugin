package com.msp.sqlformat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Pure Java 21 SQL query formatter with keyword capitalization, clause line breaking,
 * and hierarchical indentation of subqueries.
 */
public final class SqlFormatter {

    private static final Set<String> MAJOR_CLAUSES = Set.of(
            "SELECT", "FROM", "WHERE", "GROUP BY", "HAVING", "ORDER BY",
            "LIMIT", "OFFSET", "SET", "VALUES", "UNION", "UNION ALL",
            "INTERSECT", "EXCEPT", "INSERT INTO", "UPDATE", "DELETE FROM"
    );

    private static final Set<String> JOIN_CLAUSES = Set.of(
            "JOIN", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN",
            "CROSS JOIN", "LEFT OUTER JOIN", "RIGHT OUTER JOIN", "FULL OUTER JOIN",
            "NATURAL JOIN"
    );

    private static final Set<String> SINGLE_KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "FULL",
            "CROSS", "OUTER", "NATURAL", "ON", "USING", "GROUP", "ORDER", "BY",
            "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "INTERSECT", "EXCEPT",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE",
            "TABLE", "ALTER", "DROP", "TRUNCATE", "INDEX", "VIEW", "WITH", "AS",
            "DISTINCT", "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE", "ILIKE",
            "BETWEEN", "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END", "ASC",
            "DESC", "OVER", "PARTITION", "COUNT", "SUM", "AVG", "MIN", "MAX",
            "COALESCE", "NULLIF", "CAST"
    );

    private enum TokenType {
        KEYWORD,
        IDENTIFIER,
        STRING_LITERAL,
        NUMBER_LITERAL,
        COMMENT,
        OPEN_PAREN,
        CLOSE_PAREN,
        COMMA,
        SEMICOLON,
        OPERATOR,
        OTHER
    }

    private record Token(TokenType type, String text) {}

    private SqlFormatter() {}

    /**
     * Formats the SQL query using default options.
     */
    public static String format(String sql) {
        return format(sql, SqlFormatOptions.DEFAULT);
    }

    /**
     * Formats the SQL query using custom options.
     */
    public static String format(String sql, SqlFormatOptions options) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        Objects.requireNonNull(options, "options must not be null");

        List<Token> rawTokens = tokenize(sql);
        List<Token> mergedTokens = mergeCompositeKeywords(rawTokens);

        return render(mergedTokens, options);
    }

    private static List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        int len = sql.length();
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            // Whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Line comment (-- ...)
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                int start = i;
                i += 2;
                while (i < len && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
                    i++;
                }
                tokens.add(new Token(TokenType.COMMENT, sql.substring(start, i)));
                continue;
            }

            // Block comment (/* ... */)
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < len && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                if (i + 1 < len) {
                    i += 2;
                } else {
                    i = len;
                }
                tokens.add(new Token(TokenType.COMMENT, sql.substring(start, i)));
                continue;
            }

            // String literal ('...')
            if (c == '\'') {
                int start = i;
                i++;
                while (i < len) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                            i += 2; // escaped quote ''
                            continue;
                        }
                        i++;
                        break;
                    }
                    if (sql.charAt(i) == '\\' && i + 1 < len) {
                        i += 2; // escaped character \'
                        continue;
                    }
                    i++;
                }
                tokens.add(new Token(TokenType.STRING_LITERAL, sql.substring(start, i)));
                continue;
            }

            // Quoted identifiers ("...", `...`, [...])
            if (c == '"' || c == '`' || c == '[') {
                char closeChar = (c == '[') ? ']' : c;
                int start = i;
                i++;
                while (i < len && sql.charAt(i) != closeChar) {
                    i++;
                }
                if (i < len) i++;
                tokens.add(new Token(TokenType.IDENTIFIER, sql.substring(start, i)));
                continue;
            }

            // Parentheses, comma, semicolon
            if (c == '(') {
                tokens.add(new Token(TokenType.OPEN_PAREN, "("));
                i++;
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.CLOSE_PAREN, ")"));
                i++;
                continue;
            }
            if (c == ',') {
                tokens.add(new Token(TokenType.COMMA, ","));
                i++;
                continue;
            }
            if (c == ';') {
                tokens.add(new Token(TokenType.SEMICOLON, ";"));
                i++;
                continue;
            }

            // Multi-char & single-char operators
            if (c == '!' && i + 1 < len && sql.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "!="));
                i += 2;
                continue;
            }
            if (c == '<' && i + 1 < len && (sql.charAt(i + 1) == '=' || sql.charAt(i + 1) == '>')) {
                tokens.add(new Token(TokenType.OPERATOR, sql.substring(i, i + 2)));
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < len && sql.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, ">="));
                i += 2;
                continue;
            }
            if (c == '|' && i + 1 < len && sql.charAt(i + 1) == '|') {
                tokens.add(new Token(TokenType.OPERATOR, "||"));
                i += 2;
                continue;
            }
            if (c == ':' && i + 1 < len && sql.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, ":="));
                i += 2;
                continue;
            }
            if ("=<>+-*/%".indexOf(c) != -1) {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
                i++;
                continue;
            }

            // Numbers
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(sql.charAt(i)) || sql.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER_LITERAL, sql.substring(start, i)));
                continue;
            }

            // Words / Identifiers / Keywords
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_' || sql.charAt(i) == '$' || sql.charAt(i) == '.')) {
                    i++;
                }
                String word = sql.substring(start, i);
                String upper = word.toUpperCase(Locale.ROOT);
                if (SINGLE_KEYWORDS.contains(upper)) {
                    tokens.add(new Token(TokenType.KEYWORD, word));
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, word));
                }
                continue;
            }

            // Fallback for any other character
            tokens.add(new Token(TokenType.OTHER, String.valueOf(c)));
            i++;
        }

        return tokens;
    }

    private static List<Token> mergeCompositeKeywords(List<Token> raw) {
        List<Token> merged = new ArrayList<>();
        int i = 0;
        int n = raw.size();

        while (i < n) {
            Token t = raw.get(i);
            if (t.type() == TokenType.KEYWORD) {
                String u1 = t.text().toUpperCase(Locale.ROOT);

                // 3-word composites: LEFT OUTER JOIN, RIGHT OUTER JOIN, FULL OUTER JOIN, IS NOT NULL
                if (i + 2 < n && raw.get(i + 1).type() == TokenType.KEYWORD && raw.get(i + 2).type() == TokenType.KEYWORD) {
                    String u2 = raw.get(i + 1).text().toUpperCase(Locale.ROOT);
                    String u3 = raw.get(i + 2).text().toUpperCase(Locale.ROOT);
                    String tri = u1 + " " + u2 + " " + u3;
                    if (tri.equals("LEFT OUTER JOIN") || tri.equals("RIGHT OUTER JOIN") ||
                            tri.equals("FULL OUTER JOIN") || tri.equals("IS NOT NULL")) {
                        merged.add(new Token(TokenType.KEYWORD, tri));
                        i += 3;
                        continue;
                    }
                }

                // 2-word composites: GROUP BY, ORDER BY, INSERT INTO, DELETE FROM, UNION ALL,
                // INNER JOIN, LEFT JOIN, RIGHT JOIN, FULL JOIN, CROSS JOIN, NATURAL JOIN,
                // IS NULL, NOT IN, NOT EXISTS, CREATE TABLE, ALTER TABLE, DROP TABLE, TRUNCATE TABLE
                if (i + 1 < n && raw.get(i + 1).type() == TokenType.KEYWORD) {
                    String u2 = raw.get(i + 1).text().toUpperCase(Locale.ROOT);
                    String bi = u1 + " " + u2;
                    if (bi.equals("GROUP BY") || bi.equals("ORDER BY") || bi.equals("INSERT INTO") ||
                            bi.equals("DELETE FROM") || bi.equals("UNION ALL") || bi.equals("INNER JOIN") ||
                            bi.equals("LEFT JOIN") || bi.equals("RIGHT JOIN") || bi.equals("FULL JOIN") ||
                            bi.equals("CROSS JOIN") || bi.equals("NATURAL JOIN") || bi.equals("IS NULL") ||
                            bi.equals("NOT IN") || bi.equals("NOT EXISTS") || bi.equals("CREATE TABLE") ||
                            bi.equals("ALTER TABLE") || bi.equals("DROP TABLE") || bi.equals("TRUNCATE TABLE") ||
                            bi.equals("PARTITION BY")) {
                        merged.add(new Token(TokenType.KEYWORD, bi));
                        i += 2;
                        continue;
                    }
                }
            }

            merged.add(t);
            i++;
        }

        return merged;
    }

    private static String render(List<Token> tokens, SqlFormatOptions options) {
        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;
        String currentMajorClause = "";
        Deque<Boolean> subqueryStack = new ArrayDeque<>();
        int n = tokens.size();

        for (int i = 0; i < n; i++) {
            Token t = tokens.get(i);
            String upper = t.text().toUpperCase(Locale.ROOT);

            // Handle Open Paren
            if (t.type() == TokenType.OPEN_PAREN) {
                boolean isSubquery = isSubqueryParen(tokens, i + 1);
                subqueryStack.push(isSubquery);

                if (isSubquery) {
                    sb.append("(");
                    indentLevel++;
                } else {
                    sb.append("(");
                }
                continue;
            }

            // Handle Close Paren
            if (t.type() == TokenType.CLOSE_PAREN) {
                boolean wasSubquery = !subqueryStack.isEmpty() && subqueryStack.pop();
                if (wasSubquery) {
                    indentLevel = Math.max(0, indentLevel - 1);
                    newLineWithIndent(sb, indentLevel + 1, options.indentSpaces());
                    sb.append(")");
                } else {
                    sb.append(")");
                }
                continue;
            }

            // Handle Major Clauses
            if (t.type() == TokenType.KEYWORD && MAJOR_CLAUSES.contains(upper)) {
                currentMajorClause = upper;
                if (!sb.isEmpty()) {
                    int clauseIndent = (indentLevel > 0) ? (indentLevel + 1) : 0;
                    newLineWithIndent(sb, clauseIndent, options.indentSpaces());
                }
                sb.append(options.uppercaseKeywords() ? upper : t.text());

                // For SELECT, SET, GROUP BY, ORDER BY, WHERE: indent the items on next line
                if (upper.equals("SELECT") || upper.equals("SET") || upper.equals("WHERE")
                        || upper.equals("GROUP BY") || upper.equals("ORDER BY")) {
                    int bodyIndent = (indentLevel > 0) ? (indentLevel + 2) : 1;
                    newLineWithIndent(sb, bodyIndent, options.indentSpaces());
                } else {
                    sb.append(" ");
                }
                continue;
            }

            // Handle JOIN Clauses
            if (t.type() == TokenType.KEYWORD && JOIN_CLAUSES.contains(upper)) {
                if (!sb.isEmpty()) {
                    int joinIndent = (indentLevel > 0) ? (indentLevel + 1) : 0;
                    newLineWithIndent(sb, joinIndent, options.indentSpaces());
                }
                sb.append(options.uppercaseKeywords() ? upper : t.text()).append(" ");
                continue;
            }

            // Handle AND / OR within WHERE or HAVING
            if (t.type() == TokenType.KEYWORD && (upper.equals("AND") || upper.equals("OR"))) {
                boolean insideInlineParen = subqueryStack.stream().anyMatch(b -> !b);
                if (options.breakOnAndOr() && (currentMajorClause.equals("WHERE") || currentMajorClause.equals("HAVING")) && !insideInlineParen) {
                    int condIndent = (indentLevel > 0) ? (indentLevel + 2) : 1;
                    newLineWithIndent(sb, condIndent, options.indentSpaces());
                    sb.append(options.uppercaseKeywords() ? upper : t.text()).append(" ");
                    continue;
                }
            }

            // Handle Comma
            if (t.type() == TokenType.COMMA) {
                boolean insideInlineParen = subqueryStack.stream().anyMatch(b -> !b);
                if (!insideInlineParen && (currentMajorClause.equals("SELECT") || currentMajorClause.equals("SET") ||
                        currentMajorClause.equals("GROUP BY") || currentMajorClause.equals("ORDER BY"))) {
                    if (options.breakAfterSelectComma()) {
                        sb.append(",");
                        int commaIndent = (indentLevel > 0) ? (indentLevel + 2) : 1;
                        newLineWithIndent(sb, commaIndent, options.indentSpaces());
                    } else {
                        sb.append(", ");
                    }
                } else {
                    sb.append(", ");
                }
                continue;
            }

            // Handle Semicolon
            if (t.type() == TokenType.SEMICOLON) {
                sb.append(";");
                continue;
            }

            // Handle Operators
            if (t.type() == TokenType.OPERATOR) {
                String op = t.text();
                // Special case for asterisk: if preceding token was ( or function, or following token is ), avoid extra space
                if (op.equals("*") && i > 0 && tokens.get(i - 1).type() == TokenType.OPEN_PAREN) {
                    sb.append("*");
                } else {
                    ensureTrailingSpace(sb);
                    sb.append(op).append(" ");
                }
                continue;
            }

            // Handle String Literals: preserve verbatim
            if (t.type() == TokenType.STRING_LITERAL) {
                sb.append(t.text());
                if (shouldAppendSpaceAfter(tokens, i)) {
                    sb.append(" ");
                }
                continue;
            }

            // Handle Comments
            if (t.type() == TokenType.COMMENT) {
                ensureTrailingSpace(sb);
                sb.append(t.text());
                newLineWithIndent(sb, indentLevel, options.indentSpaces());
                continue;
            }

            // Keywords and Identifiers
            String text = t.text();
            if (t.type() == TokenType.KEYWORD && options.uppercaseKeywords()) {
                text = upper;
            }

            sb.append(text);

            if (shouldAppendSpaceAfter(tokens, i)) {
                sb.append(" ");
            }
        }

        return sb.toString().stripTrailing();
    }

    private static boolean isSubqueryParen(List<Token> tokens, int nextIdx) {
        int i = nextIdx;
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            if (t.type() == TokenType.COMMENT) {
                i++;
                continue;
            }
            if (t.type() == TokenType.KEYWORD) {
                String u = t.text().toUpperCase(Locale.ROOT);
                return u.equals("SELECT") || u.equals("WITH");
            }
            return false;
        }
        return false;
    }

    private static void newLineWithIndent(StringBuilder sb, int indentLevel, int indentSpaces) {
        // Strip trailing spaces from previous line
        while (!sb.isEmpty() && (sb.charAt(sb.length() - 1) == ' ' || sb.charAt(sb.length() - 1) == '\t')) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("\n");
        sb.append(" ".repeat(Math.max(0, indentLevel * indentSpaces)));
    }

    private static void ensureTrailingSpace(StringBuilder sb) {
        if (!sb.isEmpty() && !Character.isWhitespace(sb.charAt(sb.length() - 1)) &&
                sb.charAt(sb.length() - 1) != '(') {
            sb.append(" ");
        }
    }

    private static boolean shouldAppendSpaceAfter(List<Token> tokens, int currentIdx) {
        if (currentIdx + 1 >= tokens.size()) {
            return false;
        }
        Token next = tokens.get(currentIdx + 1);
        if (next.type() == TokenType.COMMA || next.type() == TokenType.SEMICOLON ||
                next.type() == TokenType.CLOSE_PAREN) {
            return false;
        }
        return true;
    }
}
