package com.msp.invindex;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses boolean search query strings into an Abstract Syntax Tree (QueryNode).
 * Supports AND, OR, NOT, grouping with parentheses, and implicit AND.
 */
public final class BooleanQueryParser {

    private enum TokenType {
        AND, OR, NOT, LPAREN, RPAREN, TERM
    }

    private record Token(TokenType type, String value) {
    }

    private static final Pattern TOKEN_REGEX = Pattern.compile("\\(|\\)|\\bAND\\b|\\bOR\\b|\\bNOT\\b|[\\p{L}\\p{N}_-]+", Pattern.CASE_INSENSITIVE);

    public QueryNode parse(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be null or blank");
        }

        List<Token> tokens = tokenizeQuery(query);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Query produced no valid tokens: " + query);
        }

        ParserState state = new ParserState(tokens);
        QueryNode root = parseOr(state);
        if (state.hasMore()) {
            throw new IllegalArgumentException("Unexpected trailing tokens in query near: " + state.peek().value());
        }
        return root;
    }

    private List<Token> tokenizeQuery(String query) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_REGEX.matcher(query);
        while (matcher.find()) {
            String match = matcher.group();
            switch (match.toUpperCase(Locale.ROOT)) {
                case "AND" -> tokens.add(new Token(TokenType.AND, match));
                case "OR" -> tokens.add(new Token(TokenType.OR, match));
                case "NOT" -> tokens.add(new Token(TokenType.NOT, match));
                case "(" -> tokens.add(new Token(TokenType.LPAREN, match));
                case ")" -> tokens.add(new Token(TokenType.RPAREN, match));
                default -> tokens.add(new Token(TokenType.TERM, match.toLowerCase(Locale.ROOT)));
            }
        }
        return tokens;
    }

    private QueryNode parseOr(ParserState state) {
        List<QueryNode> operands = new ArrayList<>();
        operands.add(parseAnd(state));

        while (state.hasMore() && state.peek().type() == TokenType.OR) {
            state.consume(); // consume 'OR'
            operands.add(parseAnd(state));
        }

        return operands.size() == 1 ? operands.get(0) : new QueryNode.OrNode(operands);
    }

    private QueryNode parseAnd(ParserState state) {
        List<QueryNode> operands = new ArrayList<>();
        operands.add(parseUnary(state));

        while (state.hasMore() && state.peek().type() != TokenType.OR && state.peek().type() != TokenType.RPAREN) {
            if (state.peek().type() == TokenType.AND) {
                state.consume(); // consume explicit 'AND'
            }
            if (state.hasMore() && state.peek().type() != TokenType.OR && state.peek().type() != TokenType.RPAREN) {
                operands.add(parseUnary(state));
            }
        }

        return operands.size() == 1 ? operands.get(0) : new QueryNode.AndNode(operands);
    }

    private QueryNode parseUnary(ParserState state) {
        if (!state.hasMore()) {
            throw new IllegalArgumentException("Unexpected end of query after operator");
        }
        if (state.peek().type() == TokenType.NOT) {
            state.consume();
            QueryNode operand = parseUnary(state);
            return new QueryNode.NotNode(operand);
        }
        return parsePrimary(state);
    }

    private QueryNode parsePrimary(ParserState state) {
        if (!state.hasMore()) {
            throw new IllegalArgumentException("Unexpected end of query");
        }

        Token token = state.consume();
        if (token.type() == TokenType.TERM) {
            return new QueryNode.TermNode(token.value());
        }

        if (token.type() == TokenType.LPAREN) {
            QueryNode expr = parseOr(state);
            if (!state.hasMore() || state.consume().type() != TokenType.RPAREN) {
                throw new IllegalArgumentException("Unclosed parenthesis in query");
            }
            return expr;
        }

        throw new IllegalArgumentException("Unexpected token in query: " + token.value());
    }

    private static final class ParserState {
        private final List<Token> tokens;
        private int pos = 0;

        ParserState(List<Token> tokens) {
            this.tokens = tokens;
        }

        boolean hasMore() {
            return pos < tokens.size();
        }

        Token peek() {
            if (!hasMore()) {
                throw new NoSuchElementException("No more tokens");
            }
            return tokens.get(pos);
        }

        Token consume() {
            if (!hasMore()) {
                throw new NoSuchElementException("No more tokens");
            }
            return tokens.get(pos++);
        }
    }
}
