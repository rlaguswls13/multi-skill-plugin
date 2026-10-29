package com.msp.interpolate;

/**
 * Strategy applied when a referenced variable or index cannot be resolved.
 */
public enum MissingStrategy {
    /**
     * Preserves the original placeholder tag as-is (e.g., "${missing}").
     */
    PRESERVE,

    /**
     * Replaces the unresolved placeholder with an empty string ("").
     */
    EMPTY,

    /**
     * Throws a {@link MissingVariableException} when a variable is unresolved.
     */
    THROW
}
