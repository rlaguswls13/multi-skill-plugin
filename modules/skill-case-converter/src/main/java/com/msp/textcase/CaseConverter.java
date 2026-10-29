package com.msp.textcase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * High-performance, zero-dependency string case converter.
 * Supports camelCase, PascalCase, snake_case, kebab-case, CONSTANT_CASE, and Title Case.
 * Correctly handles acronyms (e.g. "JSONParser" -> "json_parser") and mixed alphanumeric identifiers.
 */
public final class CaseConverter {

    private CaseConverter() {
        // Utility class
    }

    /**
     * Converts the string to lowerCamelCase (e.g. "myVariableName").
     */
    public static String toCamelCase(String str) {
        return convert(str, CaseFormat.CAMEL_CASE);
    }

    /**
     * Converts the string to PascalCase (UpperCamelCase, e.g. "MyVariableName").
     */
    public static String toPascalCase(String str) {
        return convert(str, CaseFormat.PASCAL_CASE);
    }

    /**
     * Converts the string to snake_case (e.g. "my_variable_name").
     */
    public static String toSnakeCase(String str) {
        return convert(str, CaseFormat.SNAKE_CASE);
    }

    /**
     * Converts the string to kebab-case (e.g. "my-variable-name").
     */
    public static String toKebabCase(String str) {
        return convert(str, CaseFormat.KEBAB_CASE);
    }

    /**
     * Converts the string to CONSTANT_CASE (SCREAMING_SNAKE_CASE, e.g. "MY_VARIABLE_NAME").
     */
    public static String toConstantCase(String str) {
        return convert(str, CaseFormat.CONSTANT_CASE);
    }

    /**
     * Converts the string to Title Case (e.g. "My Variable Name").
     */
    public static String toTitleCase(String str) {
        return convert(str, CaseFormat.TITLE_CASE);
    }

    /**
     * Converts the string to the specified {@link CaseFormat}.
     *
     * @param str          input string, may be null
     * @param targetFormat target casing format, must not be null
     * @return converted string, or null if input was null
     */
    public static String convert(String str, CaseFormat targetFormat) {
        Objects.requireNonNull(targetFormat, "targetFormat must not be null");
        if (str == null) {
            return null;
        }
        if (str.isEmpty()) {
            return "";
        }
        List<String> words = splitWords(str);
        if (words.isEmpty()) {
            return "";
        }
        return targetFormat.format(words);
    }

    /**
     * Splits an identifier or sentence into constituent word tokens, respecting
     * camelCase, acronyms (e.g., "JSONParser" -> ["JSON", "Parser"]), and standard delimiters.
     *
     * @param input the input string
     * @return an unmodifiable list of word tokens
     */
    public static List<String> splitWords(String input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int len = input.length();

        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);

            if (isDelimiter(c)) {
                if (!current.isEmpty()) {
                    words.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            if (!current.isEmpty()) {
                char prev = input.charAt(i - 1);
                char next = (i + 1 < len) ? input.charAt(i + 1) : '\0';

                // Case 1: Lowercase or Digit followed by Uppercase (e.g., "fooBar", "item1Bar")
                boolean lowerOrDigitToCurrentUpper = (Character.isLowerCase(prev) || Character.isDigit(prev))
                        && Character.isUpperCase(c);

                // Case 2: Uppercase sequence followed by Uppercase + Lowercase (e.g., "JSONParser" -> boundary at 'P')
                boolean acronymBoundary = Character.isUpperCase(prev)
                        && Character.isUpperCase(c)
                        && Character.isLowerCase(next);

                if (lowerOrDigitToCurrentUpper || acronymBoundary) {
                    words.add(current.toString());
                    current.setLength(0);
                }
            }

            current.append(c);
        }

        if (!current.isEmpty()) {
            words.add(current.toString());
        }

        return Collections.unmodifiableList(words);
    }

    private static boolean isDelimiter(char c) {
        return c == '_' || c == '-' || c == ' ' || c == '\t' || c == '\n' || c == '\r'
                || c == '.' || c == '/' || c == '\\' || c == ':' || c == ';';
    }
}
