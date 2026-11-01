package com.msp.sqlvisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight, zero-dependency SQL parser that produces an AST {@link SelectQueryNode}.
 * Supports standard SELECT, FROM, JOIN, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT, and OFFSET clauses.
 */
public class SimpleSqlAstParser {

    private final String sql;
    private final List<Token> tokens;
    private int pos = 0;

    public SimpleSqlAstParser(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL string cannot be null or empty");
        }
        this.sql = sql.trim();
        this.tokens = tokenize(this.sql);
    }

    public static SelectQueryNode parse(String sql) {
        return new SimpleSqlAstParser(sql).parseQuery();
    }

    public SelectQueryNode parseQuery() {
        expectKeyword("SELECT");
        List<SqlNode> projections = parseProjections();

        TableRefNode fromTable = null;
        if (matchKeyword("FROM")) {
            fromTable = parseTableRef();
        }

        List<JoinNode> joins = new ArrayList<>();
        while (checkJoin()) {
            joins.add(parseJoin());
        }

        SqlNode where = null;
        if (matchKeyword("WHERE")) {
            where = parseExpression();
        }

        List<SqlNode> groupBy = new ArrayList<>();
        if (matchKeyword("GROUP") && matchKeyword("BY")) {
            groupBy = parseExpressionList();
        }

        SqlNode having = null;
        if (matchKeyword("HAVING")) {
            having = parseExpression();
        }

        List<OrderByNode> orderBy = new ArrayList<>();
        if (matchKeyword("ORDER") && matchKeyword("BY")) {
            orderBy = parseOrderByList();
        }

        Integer limit = null;
        Integer offset = null;
        if (matchKeyword("LIMIT")) {
            Token num = expect(TokenType.NUMBER);
            limit = Integer.parseInt(num.text());
            if (matchKeyword("OFFSET")) {
                Token off = expect(TokenType.NUMBER);
                offset = Integer.parseInt(off.text());
            }
        } else if (matchKeyword("OFFSET")) {
            Token off = expect(TokenType.NUMBER);
            offset = Integer.parseInt(off.text());
        }

        return new SelectQueryNode(projections, fromTable, joins, where, groupBy, having, orderBy, limit, offset);
    }

    private List<SqlNode> parseProjections() {
        List<SqlNode> list = new ArrayList<>();
        do {
            if (match(TokenType.STAR)) {
                list.add(new WildcardNode());
            } else {
                SqlNode expr = parsePrimaryExpression();
                if (check(TokenType.IDENTIFIER) && !isClauseKeyword(peek().text())) {
                    // projection alias (e.g., col AS alias or col alias)
                    if (matchKeyword("AS")) {
                        advance(); // consume alias
                    } else {
                        advance();
                    }
                }
                list.add(expr);
            }
        } while (match(TokenType.COMMA));
        return list;
    }

    private TableRefNode parseTableRef() {
        Token first = expect(TokenType.IDENTIFIER);
        String schema = null;
        String table = first.text();

        if (match(TokenType.DOT)) {
            schema = first.text();
            Token sec = expect(TokenType.IDENTIFIER);
            table = sec.text();
        }

        String alias = null;
        if (matchKeyword("AS")) {
            alias = expect(TokenType.IDENTIFIER).text();
        } else if (check(TokenType.IDENTIFIER) && !isClauseKeyword(peek().text()) && !isJoinKeyword(peek().text())) {
            alias = advance().text();
        }

        return new TableRefNode(schema, table, alias);
    }

    private boolean checkJoin() {
        if (checkKeyword("JOIN") || checkKeyword("INNER") || checkKeyword("LEFT") || checkKeyword("RIGHT") || checkKeyword("FULL") || checkKeyword("CROSS")) {
            return true;
        }
        return false;
    }

    private JoinNode parseJoin() {
        JoinType type = JoinType.INNER;
        if (matchKeyword("LEFT")) {
            matchKeyword("OUTER");
            expectKeyword("JOIN");
            type = JoinType.LEFT;
        } else if (matchKeyword("RIGHT")) {
            matchKeyword("OUTER");
            expectKeyword("JOIN");
            type = JoinType.RIGHT;
        } else if (matchKeyword("FULL")) {
            matchKeyword("OUTER");
            expectKeyword("JOIN");
            type = JoinType.FULL;
        } else if (matchKeyword("CROSS")) {
            expectKeyword("JOIN");
            type = JoinType.CROSS;
        } else if (matchKeyword("INNER")) {
            expectKeyword("JOIN");
            type = JoinType.INNER;
        } else {
            expectKeyword("JOIN");
        }

        TableRefNode table = parseTableRef();
        SqlNode condition = null;
        if (type != JoinType.CROSS && matchKeyword("ON")) {
            condition = parseExpression();
        }

        return new JoinNode(type, table, condition);
    }

    private List<SqlNode> parseExpressionList() {
        List<SqlNode> list = new ArrayList<>();
        do {
            list.add(parseExpression());
        } while (match(TokenType.COMMA));
        return list;
    }

    private List<OrderByNode> parseOrderByList() {
        List<OrderByNode> list = new ArrayList<>();
        do {
            SqlNode expr = parsePrimaryExpression();
            SortDirection dir = SortDirection.ASC;
            if (matchKeyword("DESC")) {
                dir = SortDirection.DESC;
            } else if (matchKeyword("ASC")) {
                dir = SortDirection.ASC;
            }
            list.add(new OrderByNode(expr, dir));
        } while (match(TokenType.COMMA));
        return list;
    }

    // Expression precedence: OR -> AND -> Comparisons (=, !=, <, >, LIKE, IN) -> Primary
    public SqlNode parseExpression() {
        return parseOr();
    }

    private SqlNode parseOr() {
        SqlNode left = parseAnd();
        while (matchKeyword("OR")) {
            SqlNode right = parseAnd();
            left = new BinaryOpNode(left, "OR", right);
        }
        return left;
    }

    private SqlNode parseAnd() {
        SqlNode left = parseComparison();
        while (matchKeyword("AND")) {
            SqlNode right = parseComparison();
            left = new BinaryOpNode(left, "AND", right);
        }
        return left;
    }

    private SqlNode parseComparison() {
        SqlNode left = parsePrimaryExpression();

        if (match(TokenType.OPERATOR)) {
            String op = previous().text();
            SqlNode right = parsePrimaryExpression();
            return new BinaryOpNode(left, op, right);
        }

        if (matchKeyword("LIKE")) {
            SqlNode right = parsePrimaryExpression();
            return new BinaryOpNode(left, "LIKE", right);
        }

        if (matchKeyword("ILIKE")) {
            SqlNode right = parsePrimaryExpression();
            return new BinaryOpNode(left, "ILIKE", right);
        }

        boolean notIn = false;
        if (matchKeyword("NOT")) {
            expectKeyword("IN");
            notIn = true;
        } else if (matchKeyword("IN")) {
            notIn = false;
        } else {
            return left;
        }

        expect(TokenType.LPAREN);
        List<SqlNode> values = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                values.add(parsePrimaryExpression());
            } while (match(TokenType.COMMA));
        }
        expect(TokenType.RPAREN);

        return new InListNode(left, values, notIn);
    }

    private SqlNode parsePrimaryExpression() {
        if (match(TokenType.LPAREN)) {
            SqlNode expr = parseExpression();
            expect(TokenType.RPAREN);
            return expr;
        }

        if (match(TokenType.STRING)) {
            return LiteralNode.ofString(previous().text());
        }

        if (match(TokenType.NUMBER)) {
            String numStr = previous().text();
            if (numStr.contains(".")) {
                return LiteralNode.ofNumber(Double.parseDouble(numStr));
            } else {
                return LiteralNode.ofNumber(Long.parseLong(numStr));
            }
        }

        if (matchKeyword("TRUE")) {
            return LiteralNode.ofBoolean(true);
        }
        if (matchKeyword("FALSE")) {
            return LiteralNode.ofBoolean(false);
        }
        if (matchKeyword("NULL")) {
            return LiteralNode.ofNull();
        }

        if (match(TokenType.STAR)) {
            return new WildcardNode();
        }

        if (check(TokenType.IDENTIFIER)) {
            Token ident = advance();
            // Check if function call: ident(...)
            if (match(TokenType.LPAREN)) {
                List<SqlNode> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    do {
                        if (match(TokenType.STAR)) {
                            args.add(new WildcardNode());
                        } else {
                            args.add(parsePrimaryExpression());
                        }
                    } while (match(TokenType.COMMA));
                }
                expect(TokenType.RPAREN);
                return new FunctionCallNode(ident.text(), args);
            }

            // Check if qualified column: ident.col or ident.*
            if (match(TokenType.DOT)) {
                if (match(TokenType.STAR)) {
                    return new WildcardNode(ident.text());
                }
                Token col = expect(TokenType.IDENTIFIER);
                return new ColumnRefNode(ident.text(), col.text());
            }

            return new ColumnRefNode(ident.text());
        }

        throw new IllegalArgumentException("Unexpected token at position " + pos + ": " + (pos < tokens.size() ? tokens.get(pos) : "EOF"));
    }

    private boolean isClauseKeyword(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "FROM", "WHERE", "GROUP", "HAVING", "ORDER", "LIMIT", "OFFSET", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS" -> true;
            default -> false;
        };
    }

    private boolean isJoinKeyword(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS" -> true;
            default -> false;
        };
    }

    // Tokenizer
    private enum TokenType {
        IDENTIFIER, NUMBER, STRING, OPERATOR, COMMA, DOT, LPAREN, RPAREN, STAR, EOF
    }

    private record Token(TokenType type, String text, int start) {}

    private static List<Token> tokenize(String sql) {
        List<Token> list = new ArrayList<>();
        int i = 0;
        int len = sql.length();

        while (i < len) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == ',') {
                list.add(new Token(TokenType.COMMA, ",", i++));
            } else if (c == '.') {
                list.add(new Token(TokenType.DOT, ".", i++));
            } else if (c == '(') {
                list.add(new Token(TokenType.LPAREN, "(", i++));
            } else if (c == ')') {
                list.add(new Token(TokenType.RPAREN, ")", i++));
            } else if (c == '*') {
                list.add(new Token(TokenType.STAR, "*", i++));
            } else if (c == '\'') {
                // String literal
                int start = i++;
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                        } else {
                            i++;
                            break;
                        }
                    } else {
                        sb.append(sql.charAt(i++));
                    }
                }
                list.add(new Token(TokenType.STRING, sb.toString(), start));
            } else if (c == '=' || c == '<' || c == '>' || c == '!') {
                int start = i++;
                if (i < len) {
                    char next = sql.charAt(i);
                    if ((c == '<' && (next == '>' || next == '=')) ||
                        (c == '>' && next == '=') ||
                        (c == '!' && next == '=')) {
                        i++;
                        list.add(new Token(TokenType.OPERATOR, sql.substring(start, i), start));
                        continue;
                    }
                }
                list.add(new Token(TokenType.OPERATOR, sql.substring(start, i), start));
            } else if (Character.isDigit(c)) {
                int start = i++;
                while (i < len && (Character.isDigit(sql.charAt(i)) || sql.charAt(i) == '.')) {
                    i++;
                }
                list.add(new Token(TokenType.NUMBER, sql.substring(start, i), start));
            } else if (Character.isLetter(c) || c == '_' || c == '`' || c == '"') {
                int start = i++;
                boolean quoted = (c == '`' || c == '"');
                char quoteChar = c;
                if (quoted) {
                    StringBuilder sb = new StringBuilder();
                    while (i < len && sql.charAt(i) != quoteChar) {
                        sb.append(sql.charAt(i++));
                    }
                    if (i < len && sql.charAt(i) == quoteChar) i++;
                    list.add(new Token(TokenType.IDENTIFIER, sb.toString(), start));
                } else {
                    while (i < len && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                        i++;
                    }
                    list.add(new Token(TokenType.IDENTIFIER, sql.substring(start, i), start));
                }
            } else {
                i++;
            }
        }
        list.add(new Token(TokenType.EOF, "", len));
        return list;
    }

    // Helper navigation
    private boolean check(TokenType type) {
        if (isAtEnd()) return type == TokenType.EOF;
        return tokens.get(pos).type() == type;
    }

    private boolean checkKeyword(String kw) {
        if (isAtEnd()) return false;
        Token t = tokens.get(pos);
        return t.type() == TokenType.IDENTIFIER && t.text().equalsIgnoreCase(kw);
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean matchKeyword(String kw) {
        if (checkKeyword(kw)) {
            advance();
            return true;
        }
        return false;
    }

    private Token advance() {
        if (!isAtEnd()) pos++;
        return previous();
    }

    private boolean isAtEnd() {
        return pos >= tokens.size() || tokens.get(pos).type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token previous() {
        return tokens.get(pos - 1);
    }

    private Token expect(TokenType type) {
        if (check(type)) return advance();
        throw new IllegalArgumentException("Expected token " + type + " but got " + (pos < tokens.size() ? tokens.get(pos) : "EOF"));
    }

    private void expectKeyword(String kw) {
        if (!matchKeyword(kw)) {
            throw new IllegalArgumentException("Expected keyword '" + kw + "' at position " + pos);
        }
    }
}
