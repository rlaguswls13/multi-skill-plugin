package com.msp.htmlstrip;

/**
 * Configuration options for stripping HTML tags and unescaping entities.
 *
 * @param preserveNewlines    whether to convert block tags (p, div, br, etc.) to newlines (default: true)
 * @param unescapeEntities    whether to decode HTML entities (&amp;, &lt;, &euro;, &#39;, etc.) (default: true)
 * @param normalizeWhitespace whether to collapse redundant spaces and blank lines (default: true)
 */
public record StripOptions(
        boolean preserveNewlines,
        boolean unescapeEntities,
        boolean normalizeWhitespace
) {
    public static final StripOptions DEFAULT = new StripOptions(true, true, true);
    public static final StripOptions SINGLE_LINE = new StripOptions(false, true, true);

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean preserveNewlines = true;
        private boolean unescapeEntities = true;
        private boolean normalizeWhitespace = true;

        public Builder preserveNewlines(boolean preserveNewlines) {
            this.preserveNewlines = preserveNewlines;
            return this;
        }

        public Builder unescapeEntities(boolean unescapeEntities) {
            this.unescapeEntities = unescapeEntities;
            return this;
        }

        public Builder normalizeWhitespace(boolean normalizeWhitespace) {
            this.normalizeWhitespace = normalizeWhitespace;
            return this;
        }

        public StripOptions build() {
            return new StripOptions(preserveNewlines, unescapeEntities, normalizeWhitespace);
        }
    }
}
