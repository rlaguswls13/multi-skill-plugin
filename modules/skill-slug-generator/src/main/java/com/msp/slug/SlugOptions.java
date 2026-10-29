package com.msp.slug;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration options for generating SEO-friendly URL slugs.
 *
 * @param separator              The delimiter separating words (e.g., "-" or "_").
 * @param lowercase              Whether to convert the resulting slug to lower-case.
 * @param allowUnicode           Whether to preserve Unicode letters (Hangul, Kanji, Cyrillic, etc.).
 * @param maxLength              Maximum length of the slug (0 or negative means unlimited).
 * @param transliterateAccents   Whether to decompose and strip Latin diacritical accents (e.g., é -> e).
 * @param wordBoundaryTruncation Whether truncation should snap back to the last separator boundary.
 * @param customReplacements     Custom character/symbol replacement mapping.
 */
public record SlugOptions(
        String separator,
        boolean lowercase,
        boolean allowUnicode,
        int maxLength,
        boolean transliterateAccents,
        boolean wordBoundaryTruncation,
        Map<String, String> customReplacements
) {
    public static final SlugOptions DEFAULT = new SlugOptions(
            "-",
            true,
            false,
            0,
            true,
            true,
            defaultReplacements()
    );

    public static final SlugOptions UNICODE = new SlugOptions(
            "-",
            true,
            true,
            0,
            true,
            true,
            defaultReplacements()
    );

    public SlugOptions {
        separator = (separator == null || separator.isEmpty()) ? "-" : separator;
        customReplacements = (customReplacements == null)
                ? defaultReplacements()
                : Collections.unmodifiableMap(new HashMap<>(customReplacements));
    }

    public static Map<String, String> defaultReplacements() {
        Map<String, String> map = new HashMap<>();
        map.put("&", "and");
        map.put("@", "at");
        map.put("æ", "ae");
        map.put("Æ", "ae");
        map.put("œ", "oe");
        map.put("Œ", "oe");
        map.put("ß", "ss");
        map.put("ø", "oe");
        map.put("Ø", "oe");
        map.put("đ", "d");
        map.put("Đ", "d");
        map.put("þ", "th");
        map.put("Þ", "th");
        return Collections.unmodifiableMap(map);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String separator = "-";
        private boolean lowercase = true;
        private boolean allowUnicode = false;
        private int maxLength = 0;
        private boolean transliterateAccents = true;
        private boolean wordBoundaryTruncation = true;
        private final Map<String, String> customReplacements = new HashMap<>(defaultReplacements());

        public Builder separator(String separator) {
            this.separator = separator;
            return this;
        }

        public Builder lowercase(boolean lowercase) {
            this.lowercase = lowercase;
            return this;
        }

        public Builder allowUnicode(boolean allowUnicode) {
            this.allowUnicode = allowUnicode;
            return this;
        }

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder transliterateAccents(boolean transliterateAccents) {
            this.transliterateAccents = transliterateAccents;
            return this;
        }

        public Builder wordBoundaryTruncation(boolean wordBoundaryTruncation) {
            this.wordBoundaryTruncation = wordBoundaryTruncation;
            return this;
        }

        public Builder addReplacement(String target, String replacement) {
            this.customReplacements.put(Objects.requireNonNull(target), Objects.requireNonNull(replacement));
            return this;
        }

        public SlugOptions build() {
            return new SlugOptions(
                    separator,
                    lowercase,
                    allowUnicode,
                    maxLength,
                    transliterateAccents,
                    wordBoundaryTruncation,
                    customReplacements
            );
        }
    }
}
