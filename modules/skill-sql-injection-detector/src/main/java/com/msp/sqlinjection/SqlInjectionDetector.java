package com.msp.sqlinjection;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 동적 SQL 조립 또는 사용자 입력 파라미터에서
 * SQL Injection 공격 패턴을 사전에 탐지하고 차단하는 보안 검사기.
 */
public final class SqlInjectionDetector {

    private record Rule(String name, Pattern pattern) {}

    private static final List<Rule> RULES = List.of(
            new Rule("TAUTOLOGY_OR", Pattern.compile("(?i)(?:'|\"|\\b)\\s*or\\s*['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+", Pattern.CASE_INSENSITIVE)),
            new Rule("TAUTOLOGY_AND", Pattern.compile("(?i)(?:'|\"|\\b)\\s*and\\s*['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+", Pattern.CASE_INSENSITIVE)),
            new Rule("UNION_SELECT", Pattern.compile("(?i)\\bunion\\s+(?:all\\s+)?select\\b", Pattern.CASE_INSENSITIVE)),
            new Rule("STACKED_QUERIES", Pattern.compile("(?i);\\s*(?:drop|delete|truncate|alter|insert|update|create)\\b", Pattern.CASE_INSENSITIVE)),
            new Rule("COMMENT_INJECTION", Pattern.compile("(?i)(?:--|/\\*|\\*/|#\\s)", Pattern.CASE_INSENSITIVE)),
            new Rule("EXEC_COMMAND", Pattern.compile("(?i)\\b(?:xp_cmdshell|exec(?:ute)?\\s*\\()", Pattern.CASE_INSENSITIVE)),
            new Rule("TIME_BLIND", Pattern.compile("(?i)\\b(?:sleep|benchmark|waitfor\\s+delay)\\s*\\(", Pattern.CASE_INSENSITIVE))
    );

    private SqlInjectionDetector() {}

    public static SqlInjectionDetection detect(String input) {
        if (input == null || input.isBlank()) {
            return SqlInjectionDetection.safe();
        }

        for (Rule rule : RULES) {
            Matcher m = rule.pattern.matcher(input);
            if (m.find()) {
                return SqlInjectionDetection.danger(rule.name, m.group());
            }
        }
        return SqlInjectionDetection.safe();
    }

    public static boolean isSafe(String input) {
        return !detect(input).detected();
    }

    public static void assertSafe(String input) {
        SqlInjectionDetection det = detect(input);
        if (det.detected()) {
            throw new SqlInjectionException(String.format("Potential SQL Injection detected by rule '%s' matching '%s'", det.ruleName(), det.matchedText()));
        }
    }
}
