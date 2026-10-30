package com.msp.expr;

import java.util.*;

/**
 * 3rd party 라이브러리(SpEL/OGNL 등) 없이
 * 산술, 비교, 논리 연산자 및 변수 치환을 지원하는 순수 Java 21 경량 동적 표현식 평가기.
 */
public final class ExpressionEvaluator {

    private ExpressionEvaluator() {}

    public static Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        Map<String, Object> ctx = (context != null) ? context : Collections.emptyMap();
        List<String> tokens = tokenize(expression);
        List<String> rpn = toRpn(tokens);
        return evaluateRpn(rpn, ctx);
    }

    public static boolean evaluateCondition(String expression, Map<String, Object> context) {
        Object res = evaluate(expression, context);
        if (res instanceof Boolean b) {
            return b;
        }
        if (res instanceof Number n) {
            return n.doubleValue() != 0;
        }
        return res != null;
    }

    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int len = expr.length();

        while (i < len) {
            char c = expr.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // String literal '...'
            if (c == '\'') {
                int end = expr.indexOf('\'', i + 1);
                if (end == -1) {
                    throw new IllegalArgumentException("Unterminated string literal at " + i);
                }
                tokens.add(expr.substring(i, end + 1));
                i = end + 1;
                continue;
            }

            // Two-character operators: ==, !=, <=, >=, &&, ||
            if (i + 1 < len) {
                String two = expr.substring(i, i + 2);
                if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")
                        || two.equals("&&") || two.equals("||")) {
                    tokens.add(two);
                    i += 2;
                    continue;
                }
            }

            // Single character operators & parentheses
            if ("+-*/%<>()!".indexOf(c) != -1) {
                tokens.add(String.valueOf(c));
                i++;
                continue;
            }

            // Number or Identifier / keyword
            int start = i;
            while (i < len && (Character.isLetterOrDigit(expr.charAt(i)) || expr.charAt(i) == '.' || expr.charAt(i) == '_')) {
                i++;
            }
            if (start < i) {
                tokens.add(expr.substring(start, i));
            } else {
                throw new IllegalArgumentException("Unexpected character at " + i + ": " + c);
            }
        }
        return tokens;
    }

    private static int precedence(String op) {
        return switch (op) {
            case "||" -> 1;
            case "&&" -> 2;
            case "==", "!=" -> 3;
            case "<", "<=", ">", ">=" -> 4;
            case "+", "-" -> 5;
            case "*", "/", "%" -> 6;
            default -> 0;
        };
    }

    private static boolean isOperator(String token) {
        return switch (token) {
            case "||", "&&", "==", "!=", "<", "<=", ">", ">=", "+", "-", "*", "/", "%" -> true;
            default -> false;
        };
    }

    private static List<String> toRpn(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Deque<String> opStack = new ArrayDeque<>();

        for (String token : tokens) {
            if (token.equals("(")) {
                opStack.push(token);
            } else if (token.equals(")")) {
                while (!opStack.isEmpty() && !opStack.peek().equals("(")) {
                    output.add(opStack.pop());
                }
                if (opStack.isEmpty()) {
                    throw new IllegalArgumentException("Mismatched parentheses");
                }
                opStack.pop(); // pop '('
            } else if (isOperator(token)) {
                while (!opStack.isEmpty() && isOperator(opStack.peek())
                        && precedence(opStack.peek()) >= precedence(token)) {
                    output.add(opStack.pop());
                }
                opStack.push(token);
            } else {
                output.add(token);
            }
        }

        while (!opStack.isEmpty()) {
            String top = opStack.pop();
            if (top.equals("(") || top.equals(")")) {
                throw new IllegalArgumentException("Mismatched parentheses");
            }
            output.add(top);
        }

        return output;
    }

    private static Object evaluateRpn(List<String> rpn, Map<String, Object> ctx) {
        Deque<Object> valStack = new ArrayDeque<>();

        for (String token : rpn) {
            if (!isOperator(token)) {
                valStack.push(resolveValue(token, ctx));
            } else {
                if (valStack.size() < 2) {
                    throw new IllegalArgumentException("Invalid expression syntax near " + token);
                }
                Object right = valStack.pop();
                Object left = valStack.pop();
                valStack.push(applyOperator(token, left, right));
            }
        }

        if (valStack.size() != 1) {
            throw new IllegalArgumentException("Invalid expression: unresolved operands");
        }
        return valStack.pop();
    }

    private static Object resolveValue(String token, Map<String, Object> ctx) {
        // String literal
        if (token.startsWith("'") && token.endsWith("'")) {
            return token.substring(1, token.length() - 1);
        }
        // Booleans
        if ("true".equalsIgnoreCase(token)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(token)) return Boolean.FALSE;
        if ("null".equalsIgnoreCase(token)) return null;

        // Number literal
        try {
            if (token.contains(".")) {
                return Double.parseDouble(token);
            }
            return Long.parseLong(token);
        } catch (NumberFormatException ignored) {}

        // Variable in context
        return ctx.get(token);
    }

    private static Object applyOperator(String op, Object left, Object right) {
        // Logical
        if (op.equals("&&")) {
            return toBool(left) && toBool(right);
        }
        if (op.equals("||")) {
            return toBool(left) || toBool(right);
        }

        // Equality
        if (op.equals("==")) {
            if (left instanceof Number n1 && right instanceof Number n2) {
                return Double.compare(n1.doubleValue(), n2.doubleValue()) == 0;
            }
            return Objects.equals(left, right);
        }
        if (op.equals("!=")) {
            if (left instanceof Number n1 && right instanceof Number n2) {
                return Double.compare(n1.doubleValue(), n2.doubleValue()) != 0;
            }
            return !Objects.equals(left, right);
        }

        // Relational comparisons
        if (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=")) {
            double d1 = toDouble(left);
            double d2 = toDouble(right);
            return switch (op) {
                case "<" -> d1 < d2;
                case "<=" -> d1 <= d2;
                case ">" -> d1 > d2;
                case ">=" -> d1 >= d2;
                default -> false;
            };
        }

        // String Concatenation
        if (left instanceof String || right instanceof String) {
            if (op.equals("+")) {
                return String.valueOf(left) + right;
            }
        }

        boolean isInteger = (left instanceof Long || left instanceof Integer) && (right instanceof Long || right instanceof Integer);
        if (isInteger) {
            long l1 = ((Number) left).longValue();
            long l2 = ((Number) right).longValue();
            return switch (op) {
                case "+" -> l1 + l2;
                case "-" -> l1 - l2;
                case "*" -> l1 * l2;
                case "/" -> {
                    if (l2 == 0) throw new ArithmeticException("Division by zero");
                    if (l1 % l2 == 0) yield l1 / l2;
                    yield (double) l1 / l2;
                }
                case "%" -> {
                    if (l2 == 0) throw new ArithmeticException("Modulo by zero");
                    yield l1 % l2;
                }
                default -> throw new UnsupportedOperationException("Unknown operator: " + op);
            };
        }

        double n1 = toDouble(left);
        double n2 = toDouble(right);

        return switch (op) {
            case "+" -> n1 + n2;
            case "-" -> n1 - n2;
            case "*" -> n1 * n2;
            case "/" -> {
                if (n2 == 0) throw new ArithmeticException("Division by zero");
                yield n1 / n2;
            }
            case "%" -> {
                if (n2 == 0) throw new ArithmeticException("Modulo by zero");
                yield n1 % n2;
            }
            default -> throw new UnsupportedOperationException("Unknown operator: " + op);
        };
    }

    private static boolean toBool(Object obj) {
        if (obj instanceof Boolean b) return b;
        if (obj instanceof Number n) return n.doubleValue() != 0;
        return obj != null;
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj == null) return 0.0;
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert to number: " + obj);
        }
    }
}
