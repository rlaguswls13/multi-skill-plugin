package com.msp.sqlformat;

/**
 * Options controlling SQL query formatting.
 *
 * @param indentSpaces         number of spaces per indentation level (default: 4)
 * @param uppercaseKeywords    whether SQL keywords should be capitalized (default: true)
 * @param breakAfterSelectComma whether to break line after each comma in SELECT column lists (default: true)
 * @param breakOnAndOr         whether to break line before AND / OR in WHERE/HAVING clauses (default: true)
 */
public record SqlFormatOptions(
        int indentSpaces,
        boolean uppercaseKeywords,
        boolean breakAfterSelectComma,
        boolean breakOnAndOr
) {
    public static final SqlFormatOptions DEFAULT = new SqlFormatOptions(4, true, true, true);

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int indentSpaces = 4;
        private boolean uppercaseKeywords = true;
        private boolean breakAfterSelectComma = true;
        private boolean breakOnAndOr = true;

        public Builder indentSpaces(int indentSpaces) {
            this.indentSpaces = Math.max(0, indentSpaces);
            return this;
        }

        public Builder uppercaseKeywords(boolean uppercaseKeywords) {
            this.uppercaseKeywords = uppercaseKeywords;
            return this;
        }

        public Builder breakAfterSelectComma(boolean breakAfterSelectComma) {
            this.breakAfterSelectComma = breakAfterSelectComma;
            return this;
        }

        public Builder breakOnAndOr(boolean breakOnAndOr) {
            this.breakOnAndOr = breakOnAndOr;
            return this;
        }

        public SqlFormatOptions build() {
            return new SqlFormatOptions(indentSpaces, uppercaseKeywords, breakAfterSelectComma, breakOnAndOr);
        }
    }
}
