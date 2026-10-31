package com.msp.sqllexer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * High-performance, zero-dependency SQL lexer and tokenizer.
 * <p>
 * Deconstructs SQL queries into {@link SqlToken} items:
 * {@link SqlTokenType#KEYWORD}, {@link SqlTokenType#IDENTIFIER}, {@link SqlTokenType#STRING_LITERAL},
 * {@link SqlTokenType#NUMERIC_LITERAL}, {@link SqlTokenType#OPERATOR}, {@link SqlTokenType#PUNCTUATION},
 * and {@link SqlTokenType#COMMENT}.
 * </p>
 */
public final class SqlLexer {

    private static final Set<String> KEYWORDS = new HashSet<>();

    static {
        String[] kw = {
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "NATURAL", "ON", "USING",
            "GROUP", "BY", "ORDER", "ASC", "DESC", "HAVING", "LIMIT", "OFFSET",
            "AND", "OR", "NOT", "IN", "IS", "NULL", "AS", "DISTINCT", "ALL",
            "CASE", "WHEN", "THEN", "ELSE", "END", "LIKE", "ILIKE", "BETWEEN",
            "UNION", "EXCEPT", "INTERSECT", "EXISTS",
            "CREATE", "ALTER", "DROP", "TRUNCATE", "TABLE", "VIEW", "INDEX", "TRIGGER",
            "PROCEDURE", "FUNCTION", "DATABASE", "SCHEMA",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK", "DEFAULT", "CONSTRAINT",
            "CASCADE", "RESTRICT", "WITH", "RECURSIVE", "TOP", "FETCH", "FIRST", "ROWS", "ONLY",
            "TRUE", "FALSE", "OVER", "PARTITION", "CAST", "COALESCE", "NULLIF"
        };
        for (String k : kw) {
            KEYWORDS.add(k.toUpperCase(Locale.ROOT));
        }
    }

    private SqlLexer() {
        // Utility class
    }

    /**
     * Tokenizes the given SQL query into a list of {@link SqlToken} items.
     *
     * @param sql source SQL query string
     * @return unmodifiable list of tokens (empty list if input is null or blank)
     */
    public static List<SqlToken> tokenize(String sql) {
        if (sql == null || sql.isEmpty()) {
            return Collections.emptyList();
        }

        List<SqlToken> tokens = new ArrayList<>();
        int len = sql.length();
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            // 1. Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            int startPos = i;

            // 2. Comments: single-line (-- or #) and multi-line (/* ... */)
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                i = scanSingleLineComment(sql, i);
                tokens.add(new SqlToken(SqlTokenType.COMMENT, sql.substring(startPos, i), startPos));
                continue;
            }
            if (c == '#') {
                i = scanSingleLineComment(sql, i);
                tokens.add(new SqlToken(SqlTokenType.COMMENT, sql.substring(startPos, i), startPos));
                continue;
            }
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                i = scanBlockComment(sql, i);
                tokens.add(new SqlToken(SqlTokenType.COMMENT, sql.substring(startPos, i), startPos));
                continue;
            }

            // 3. String literals: '...' or "..."
            if (c == '\'' || c == '"') {
                i = scanStringLiteral(sql, i, c);
                tokens.add(new SqlToken(SqlTokenType.STRING_LITERAL, sql.substring(startPos, i), startPos));
                continue;
            }

            // 4. Backtick quoted identifier `...`
            if (c == '`') {
                i = scanDelimited(sql, i, '`');
                tokens.add(new SqlToken(SqlTokenType.IDENTIFIER, sql.substring(startPos, i), startPos));
                continue;
            }

            // 5. Bracket quoted identifier [...]
            if (c == '[' && i + 1 < len && isIdentifierChar(sql.charAt(i + 1))) {
                i = scanDelimited(sql, i, ']');
                tokens.add(new SqlToken(SqlTokenType.IDENTIFIER, sql.substring(startPos, i), startPos));
                continue;
            }

            // 6. Numeric literals
            if (Character.isDigit(c) || (c == '.' && i + 1 < len && Character.isDigit(sql.charAt(i + 1)))) {
                i = scanNumber(sql, i);
                tokens.add(new SqlToken(SqlTokenType.NUMERIC_LITERAL, sql.substring(startPos, i), startPos));
                continue;
            }

            // 7. Regular identifier or Keyword
            if (isIdentifierStart(c)) {
                i = scanIdentifier(sql, i);
                String word = sql.substring(startPos, i);
                SqlTokenType type = KEYWORDS.contains(word.toUpperCase(Locale.ROOT))
                    ? SqlTokenType.KEYWORD
                    : SqlTokenType.IDENTIFIER;
                tokens.add(new SqlToken(type, word, startPos));
                continue;
            }

            // 8. Multi-character and single-character Operators
            int opEnd = scanOperator(sql, i);
            if (opEnd > i) {
                tokens.add(new SqlToken(SqlTokenType.OPERATOR, sql.substring(startPos, opEnd), startPos));
                i = opEnd;
                continue;
            }

            // 9. Punctuation
            if (isPunctuation(c)) {
                tokens.add(new SqlToken(SqlTokenType.PUNCTUATION, String.valueOf(c), startPos));
                i++;
                continue;
            }

            // Fallback: single unrecognized character as PUNCTUATION
            tokens.add(new SqlToken(SqlTokenType.PUNCTUATION, String.valueOf(c), startPos));
            i++;
        }

        return Collections.unmodifiableList(tokens);
    }

    /**
     * Tokenizes the SQL query and filters out comments.
     */
    public static List<SqlToken> tokenizeWithoutComments(String sql) {
        return tokenize(sql).stream()
            .filter(t -> t.type() != SqlTokenType.COMMENT)
            .toList();
    }

    private static int scanSingleLineComment(String sql, int start) {
        int i = start;
        int len = sql.length();
        while (i < len && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
            i++;
        }
        return i;
    }

    private static int scanBlockComment(String sql, int start) {
        int i = start + 2;
        int len = sql.length();
        while (i + 1 < len) {
            if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
                return i + 2;
            }
            i++;
        }
        return len; // Unclosed comment until EOF
    }

    private static int scanStringLiteral(String sql, int start, char quote) {
        int i = start + 1;
        int len = sql.length();
        while (i < len) {
            char c = sql.charAt(i);
            if (c == '\\' && i + 1 < len) {
                // Escaped character like \' or \"
                i += 2;
                continue;
            }
            if (c == quote) {
                // Check SQL standard doubled quote (e.g., '' or "")
                if (i + 1 < len && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return len; // Unclosed string literal until EOF
    }

    private static int scanDelimited(String sql, int start, char closeChar) {
        int i = start + 1;
        int len = sql.length();
        while (i < len) {
            if (sql.charAt(i) == closeChar) {
                return i + 1;
            }
            i++;
        }
        return len;
    }

    private static int scanNumber(String sql, int start) {
        int i = start;
        int len = sql.length();

        // Hexadecimal 0x...
        if (sql.charAt(i) == '0' && i + 1 < len && (sql.charAt(i + 1) == 'x' || sql.charAt(i + 1) == 'X')) {
            i += 2;
            while (i < len && isHexDigit(sql.charAt(i))) {
                i++;
            }
            return i;
        }

        boolean seenDot = (sql.charAt(i) == '.');
        if (seenDot) {
            i++;
        }
        while (i < len && Character.isDigit(sql.charAt(i))) {
            i++;
        }

        if (!seenDot && i < len && sql.charAt(i) == '.') {
            // Must not treat "table.column" as decimal
            if (i + 1 < len && Character.isDigit(sql.charAt(i + 1))) {
                i++;
                while (i < len && Character.isDigit(sql.charAt(i))) {
                    i++;
                }
            }
        }

        // Scientific notation: e.g., 1e10, 2.5E-3
        if (i < len && (sql.charAt(i) == 'e' || sql.charAt(i) == 'E')) {
            int next = i + 1;
            if (next < len && (sql.charAt(next) == '+' || sql.charAt(next) == '-')) {
                next++;
            }
            if (next < len && Character.isDigit(sql.charAt(next))) {
                i = next;
                while (i < len && Character.isDigit(sql.charAt(i))) {
                    i++;
                }
            }
        }

        return i;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static int scanIdentifier(String sql, int start) {
        int i = start;
        int len = sql.length();
        while (i < len && isIdentifierChar(sql.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int scanOperator(String sql, int i) {
        int len = sql.length();
        char c = sql.charAt(i);

        // 3-character operator
        if (i + 2 < len) {
            String op3 = sql.substring(i, i + 3);
            if (op3.equals("->>")) {
                return i + 3;
            }
        }

        // 2-character operators
        if (i + 1 < len) {
            String op2 = sql.substring(i, i + 2);
            if (op2.equals("!=") || op2.equals("<>") || op2.equals("<=") || op2.equals(">=")
                || op2.equals(":=") || op2.equals("||") || op2.equals("==") || op2.equals("->")
                || op2.equals("::") || op2.equals("<<") || op2.equals(">>")) {
                return i + 2;
            }
        }

        // 1-character operators
        if (c == '=' || c == '<' || c == '>' || c == '+' || c == '-' || c == '*'
            || c == '/' || c == '%' || c == '&' || c == '|' || c == '^' || c == '~' || c == '!') {
            return i + 1;
        }

        return i;
    }

    private static boolean isPunctuation(char c) {
        return c == '(' || c == ')' || c == ',' || c == ';' || c == '.'
            || c == '[' || c == ']' || c == '{' || c == '}' || c == ':';
    }
}
