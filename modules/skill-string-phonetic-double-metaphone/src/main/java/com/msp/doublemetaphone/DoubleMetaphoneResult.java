package com.msp.doublemetaphone;

/**
 * Encapsulates the primary and secondary phonetic keys produced by the Double Metaphone algorithm.
 *
 * @param primary   The primary phonetic key
 * @param secondary The secondary (alternate) phonetic key
 */
public record DoubleMetaphoneResult(String primary, String secondary) {

    public DoubleMetaphoneResult {
        primary = primary != null ? primary : "";
        secondary = secondary != null ? secondary : "";
    }

    public String getPrimary() {
        return primary;
    }

    public String getSecondary() {
        return secondary;
    }

    /**
     * Checks if this result matches another result.
     *
     * @param other     the other result
     * @param alternate if true, matches primary or secondary of both; if false, matches only primary
     * @return true if phonetic match exists
     */
    public boolean matches(DoubleMetaphoneResult other, boolean alternate) {
        if (other == null) {
            return false;
        }
        if (!alternate) {
            return !primary.isEmpty() && primary.equals(other.primary);
        }
        return (!primary.isEmpty() && (primary.equals(other.primary) || primary.equals(other.secondary)))
                || (!secondary.isEmpty() && (secondary.equals(other.primary) || secondary.equals(other.secondary)));
    }

    /**
     * Checks if this result matches another result using primary or secondary phonetic keys.
     */
    public boolean matches(DoubleMetaphoneResult other) {
        return matches(other, true);
    }
}
