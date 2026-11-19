package com.msp.envcleaner;

/**
 * Action taken when a sensitive environment variable matches a sanitization rule.
 */
public enum SanitizeAction {
    /**
     * Completely remove the sensitive variable from the environment map.
     */
    REMOVE,

    /**
     * Keep the variable key but replace its value with a redacted mask string.
     */
    MASK,

    /**
     * Preserve the variable and its original value untouched.
     */
    PRESERVE
}
