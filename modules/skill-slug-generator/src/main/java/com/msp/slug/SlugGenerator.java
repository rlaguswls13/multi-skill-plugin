package com.msp.slug;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Thread-safe SEO-friendly URL slug generator supporting multilingual inputs,
 * accent transliteration, Unicode preservation, and word-boundary safe truncation.
 */
public final class SlugGenerator {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private SlugGenerator() {
        // Utility class
    }

    /**
     * Generates a slug using default options (lowercase, hyphen separated, ASCII only).
     *
     * @param input Raw title or string.
     * @return Normalized URL slug.
     */
    public static String generate(String input) {
        return generate(input, SlugOptions.DEFAULT);
    }

    /**
     * Generates a slug using customized options.
     *
     * @param input   Raw title or string.
     * @param options Configuration options.
     * @return Normalized URL slug.
     */
    public static String generate(String input, SlugOptions options) {
        if (input == null || input.isBlank()) {
            return "";
        }
        SlugOptions opts = (options != null) ? options : SlugOptions.DEFAULT;
        String text = input.trim();

        // Step 1: Custom symbol and ligature replacements
        for (Map.Entry<String, String> entry : opts.customReplacements().entrySet()) {
            String target = entry.getKey();
            String repl = entry.getValue();
            if (text.contains(target)) {
                // If the target is non-alphanumeric (like '&', '@'), pad with spaces.
                // If it is a letter ligature (like 'æ', 'ß'), replace directly.
                if (target.length() == 1 && !Character.isLetterOrDigit(target.charAt(0))) {
                    text = text.replace(target, " " + repl + " ");
                } else {
                    text = text.replace(target, repl);
                }
            }
        }

        // Step 2: Transliterate Latin accents (e.g. é -> e, ü -> u)
        if (opts.transliterateAccents()) {
            String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
            String stripped = DIACRITICS.matcher(decomposed).replaceAll("");
            // Recompose so Unicode scripts (like Hangul) retain proper canonical form
            text = Normalizer.normalize(stripped, Normalizer.Form.NFC);
        }

        // Step 3: Lowercase conversion
        if (opts.lowercase()) {
            text = text.toLowerCase(Locale.ROOT);
        }

        // Step 4: Non-allowed character replacement with separator
        String sep = opts.separator();
        String disallowedPattern;
        if (opts.allowUnicode()) {
            // Keep Unicode letters (\p{L}) and numbers (\p{N})
            disallowedPattern = "[^\\p{L}\\p{N}]+";
        } else {
            // Keep only standard ASCII alphanumeric
            disallowedPattern = "[^a-zA-Z0-9]+";
        }
        text = text.replaceAll(disallowedPattern, sep);

        // Step 5: Collapse consecutive separators
        String quotedSep = Pattern.quote(sep);
        text = text.replaceAll(quotedSep + "{2,}", sep);

        // Step 6: Trim leading and trailing separators
        text = trimSeparators(text, sep);

        // Step 7: Truncate to maxLength if specified
        if (opts.maxLength() > 0 && text.length() > opts.maxLength()) {
            text = text.substring(0, opts.maxLength());
            if (opts.wordBoundaryTruncation()) {
                int lastSep = text.lastIndexOf(sep);
                if (lastSep > 0) {
                    text = text.substring(0, lastSep);
                }
            }
            text = trimSeparators(text, sep);
        }

        return text;
    }

    private static String trimSeparators(String text, String sep) {
        if (text.isEmpty() || sep.isEmpty()) {
            return text;
        }
        while (text.startsWith(sep)) {
            text = text.substring(sep.length());
        }
        while (text.endsWith(sep)) {
            text = text.substring(0, text.length() - sep.length());
        }
        return text;
    }
}
