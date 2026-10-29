package com.msp.htmlstrip;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, safe HTML tag stripper and entity unescaper designed for
 * full-text search indexing, content extraction, and text normalization.
 */
public final class HtmlTagStripper {

    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern STYLE_PATTERN = Pattern.compile("(?is)<style\\b[^>]*>.*?</style>");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern BLOCK_TAG_PATTERN = Pattern.compile(
            "(?i)</?(?:p|div|br|hr|h[1-6]|li|tr|td|th|blockquote|section|article|header|footer|nav|aside|main|pre)\\b[^>]*>"
    );
    private static final Pattern REMAINING_TAG_PATTERN = Pattern.compile(
            "</?[a-zA-Z!][^\">]*(?:\"[^\"]*\"|'[^']*'|[^'\">])*>"
    );
    private static final Pattern ENTITY_PATTERN = Pattern.compile("&(#?[a-zA-Z0-9]+);");

    private static final Map<String, String> NAMED_ENTITIES = Map.ofEntries(
            Map.entry("amp", "&"),
            Map.entry("lt", "<"),
            Map.entry("gt", ">"),
            Map.entry("quot", "\""),
            Map.entry("apos", "'"),
            Map.entry("nbsp", " "),
            Map.entry("copy", "©"),
            Map.entry("reg", "®"),
            Map.entry("trade", "™"),
            Map.entry("euro", "€"),
            Map.entry("pound", "£"),
            Map.entry("yen", "¥"),
            Map.entry("cent", "¢"),
            Map.entry("sect", "§"),
            Map.entry("deg", "°"),
            Map.entry("plusmn", "±"),
            Map.entry("times", "×"),
            Map.entry("divide", "÷"),
            Map.entry("bull", "•"),
            Map.entry("hellip", "…"),
            Map.entry("ndash", "–"),
            Map.entry("mdash", "—"),
            Map.entry("lsquo", "‘"),
            Map.entry("rsquo", "’"),
            Map.entry("ldquo", "“"),
            Map.entry("rdquo", "”"),
            Map.entry("laquo", "«"),
            Map.entry("raquo", "»"),
            Map.entry("para", "¶"),
            Map.entry("micro", "µ")
    );

    private HtmlTagStripper() {}

    /**
     * Strips HTML tags and unescapes entities using default multi-line options.
     */
    public static String strip(String html) {
        return strip(html, StripOptions.DEFAULT);
    }

    /**
     * Strips HTML tags and collapses the result into a clean single line.
     */
    public static String stripToSingleLine(String html) {
        return strip(html, StripOptions.SINGLE_LINE);
    }

    /**
     * Strips HTML tags and unescapes entities with custom options.
     */
    public static String strip(String html, StripOptions options) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Objects.requireNonNull(options, "options must not be null");

        // 1. Remove script, style, and comments completely with their inner contents
        String text = SCRIPT_PATTERN.matcher(html).replaceAll("");
        text = STYLE_PATTERN.matcher(text).replaceAll("");
        text = COMMENT_PATTERN.matcher(text).replaceAll("");

        // 2. Convert block-level elements into newlines or spaces
        String blockReplacement = options.preserveNewlines() ? "\n" : " ";
        text = BLOCK_TAG_PATTERN.matcher(text).replaceAll(blockReplacement);

        // 3. Remove all remaining tags
        text = REMAINING_TAG_PATTERN.matcher(text).replaceAll("");

        // 4. Unescape entities
        if (options.unescapeEntities()) {
            text = unescape(text);
        }

        // 5. Normalize whitespace
        if (options.normalizeWhitespace()) {
            text = normalizeWhitespace(text, options.preserveNewlines());
        }

        return text;
    }

    /**
     * Decodes HTML entities (&amp;, &lt;, &euro;, &#39;, &#x2F;, etc.) in text.
     */
    public static String unescape(String text) {
        if (text == null || text.indexOf('&') == -1) {
            return text;
        }

        Matcher matcher = ENTITY_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());

        while (matcher.find()) {
            String entity = matcher.group(1);
            String replacement = decodeEntity(entity);
            if (replacement != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private static String decodeEntity(String entity) {
        // Hex entity: &#x27; or &#X27;
        if (entity.startsWith("#x") || entity.startsWith("#X")) {
            try {
                int codePoint = Integer.parseInt(entity.substring(2), 16);
                if (Character.isValidCodePoint(codePoint)) {
                    return Character.toString(codePoint);
                }
            } catch (NumberFormatException ignored) {}
            return null;
        }

        // Decimal entity: &#39;
        if (entity.startsWith("#")) {
            try {
                int codePoint = Integer.parseInt(entity.substring(1), 10);
                if (Character.isValidCodePoint(codePoint)) {
                    return Character.toString(codePoint);
                }
            } catch (NumberFormatException ignored) {}
            return null;
        }

        // Named entity
        return NAMED_ENTITIES.get(entity);
    }

    private static String normalizeWhitespace(String text, boolean preserveNewlines) {
        if (!preserveNewlines) {
            return text.replaceAll("[\\s\\u00A0]+", " ").trim();
        }

        // Normalize line breaks
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');

        // Collapse horizontal spaces and non-breaking spaces on each line
        normalized = normalized.replaceAll("[ \\t\\f\\u00A0]+", " ");

        // Trim horizontal spaces around line breaks
        normalized = normalized.replaceAll(" *\\n *", "\n");

        // Collapse multiple consecutive newlines into a single newline
        normalized = normalized.replaceAll("\\n+", "\n");

        return normalized.trim();
    }
}
