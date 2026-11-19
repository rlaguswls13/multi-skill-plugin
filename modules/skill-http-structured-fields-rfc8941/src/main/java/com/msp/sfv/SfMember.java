package com.msp.sfv;

import java.util.Map;

/**
 * Member of an RFC 8941 List or Dictionary value: either an {@link SfItem} or an {@link SfInnerList}.
 */
public sealed interface SfMember permits SfItem, SfInnerList {
    Map<String, BareItem> params();

    /**
     * Validates an RFC 8941 Section 3.1.2 Key (lcalpha / "*") *( lcalpha / DIGIT / "_" / "-" / "." / "*" ).
     */
    static void validateKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new StructuredFieldException("Structured field key must not be null or empty");
        }
        char first = key.charAt(0);
        if (!isLcAlpha(first) && first != '*') {
            throw new StructuredFieldException("Structured field key must start with a-z or '*': " + key);
        }
        for (int i = 1; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!isKeyChar(c)) {
                throw new StructuredFieldException("Invalid character '" + c + "' in key: " + key, i);
            }
        }
    }

    static boolean isLcAlpha(char c) {
        return c >= 'a' && c <= 'z';
    }

    static boolean isKeyChar(char c) {
        return isLcAlpha(c)
                || (c >= '0' && c <= '9')
                || c == '_'
                || c == '-'
                || c == '.'
                || c == '*';
    }
}
