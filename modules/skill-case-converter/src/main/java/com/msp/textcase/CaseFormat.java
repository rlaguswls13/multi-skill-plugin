package com.msp.textcase;

import java.util.List;
import java.util.Locale;

/**
 * Standard string casing formats.
 */
public enum CaseFormat {
    /**
     * camelCase: lowerCamelCase, e.g. "myVariableName"
     */
    CAMEL_CASE {
        @Override
        public String format(List<String> words) {
            if (words == null || words.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (word.isEmpty()) continue;
                if (i == 0) {
                    sb.append(word.toLowerCase(Locale.ROOT));
                } else {
                    sb.append(capitalize(word));
                }
            }
            return sb.toString();
        }
    },

    /**
     * PascalCase: UpperCamelCase, e.g. "MyVariableName"
     */
    PASCAL_CASE {
        @Override
        public String format(List<String> words) {
            if (words == null || words.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                sb.append(capitalize(word));
            }
            return sb.toString();
        }
    },

    /**
     * snake_case: lower_snake_case, e.g. "my_variable_name"
     */
    SNAKE_CASE {
        @Override
        public String format(List<String> words) {
            return join(words, "_", true);
        }
    },

    /**
     * kebab-case: lower-kebab-case, e.g. "my-variable-name"
     */
    KEBAB_CASE {
        @Override
        public String format(List<String> words) {
            return join(words, "-", true);
        }
    },

    /**
     * CONSTANT_CASE: SCREAMING_SNAKE_CASE, e.g. "MY_VARIABLE_NAME"
     */
    CONSTANT_CASE {
        @Override
        public String format(List<String> words) {
            return join(words, "_", false);
        }
    },

    /**
     * Title Case: "My Variable Name"
     */
    TITLE_CASE {
        @Override
        public String format(List<String> words) {
            if (words == null || words.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (word.isEmpty()) continue;
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(capitalize(word));
            }
            return sb.toString();
        }
    };

    /**
     * Formats the list of decomposed words into the target casing style.
     *
     * @param words the word tokens
     * @return formatted string
     */
    public abstract String format(List<String> words);

    private static String join(List<String> words, String delimiter, boolean toLower) {
        if (words == null || words.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) {
                sb.append(delimiter);
            }
            sb.append(toLower ? word.toLowerCase(Locale.ROOT) : word.toUpperCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static String capitalize(String word) {
        if (word.isEmpty()) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
    }
}
