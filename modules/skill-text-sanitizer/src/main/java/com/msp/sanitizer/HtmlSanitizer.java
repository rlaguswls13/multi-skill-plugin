package com.msp.sanitizer;

import java.util.regex.Pattern;

/**
 * Thread-safe utility for HTML escaping, tag stripping, and XSS sanitization
 * without heavy external runtime dependencies.
 */
public final class HtmlSanitizer {

    // Dangerous tags with their contents stripped: <script>, <style>, <iframe>, <object>, <embed>, <applet>
    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "<script[^>]*>[\\s\\S]*?</script>", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STYLE_BLOCK = Pattern.compile(
            "<style[^>]*>[\\s\\S]*?</style>", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IFRAME_BLOCK = Pattern.compile(
            "<iframe[^>]*>[\\s\\S]*?</iframe>|<iframe[^>]*/>|<iframe[^>]*>", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OBJECT_BLOCK = Pattern.compile(
            "<object[^>]*>[\\s\\S]*?</object>|<object[^>]*/>|<object[^>]*>", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMBED_TAG = Pattern.compile(
            "<embed[^>]*>", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern APPLET_BLOCK = Pattern.compile(
            "<applet[^>]*>[\\s\\S]*?</applet>|<applet[^>]*>", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern META_TAG = Pattern.compile(
            "<meta[^>]*>", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LINK_TAG = Pattern.compile(
            "<link[^>]*>", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BASE_TAG = Pattern.compile(
            "<base[^>]*>", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SVG_BLOCK = Pattern.compile(
            "<svg[^>]*>[\\s\\S]*?</svg>|<svg[^>]*>", Pattern.CASE_INSENSITIVE
    );

    // Event handler attributes (e.g. onload=..., onerror=..., onclick=...)
    private static final Pattern ON_EVENT_QUOTED = Pattern.compile(
            "\\s+on[a-zA-Z]+\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ON_EVENT_UNQUOTED = Pattern.compile(
            "\\s+on[a-zA-Z]+\\s*=\\s*[^\\s>]+", Pattern.CASE_INSENSITIVE
    );

    // Pseudo-protocols in href/src/action/data attributes: javascript:, vbscript:, data:
    private static final Pattern JAVASCRIPT_HREF_QUOTED = Pattern.compile(
            "\\s+(href|src|action|formaction)\\s*=\\s*([\"'])\\s*(javascript|vbscript|data):.*?\\2", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern JAVASCRIPT_HREF_UNQUOTED = Pattern.compile(
            "\\s+(href|src|action|formaction)\\s*=\\s*(javascript|vbscript|data):[^\\s>]*", Pattern.CASE_INSENSITIVE
    );

    // HTML comments: <!-- ... -->
    private static final Pattern HTML_COMMENT = Pattern.compile(
            "<!--[\\s\\S]*?-->"
    );

    // General tag matcher for stripping all tags
    private static final Pattern ALL_HTML_TAGS = Pattern.compile(
            "<[^>]+>"
    );

    // Suspicious pattern check
    private static final Pattern SUSPICIOUS_XSS = Pattern.compile(
            "(?i)(<script|<iframe|<object|<embed|<svg|\\bon[a-zA-Z]+\\s*=|javascript:|vbscript:)"
    );

    private HtmlSanitizer() {
    }

    /**
     * Escapes standard HTML special characters (&amp;, &lt;, &gt;, &quot;, &#39;) to prevent injection.
     *
     * @param html raw HTML string
     * @return HTML-entity-escaped string
     */
    public static String escapeHtml(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        StringBuilder sb = new StringBuilder(html.length() + 16);
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Unescapes HTML entities back to their normal character representations.
     *
     * @param html entity-encoded string
     * @return unescaped string
     */
    public static String unescapeHtml(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return html.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    /**
     * Completely strips all HTML tags and HTML comments, leaving only raw text.
     *
     * @param html raw HTML string
     * @return text with all HTML tags stripped
     */
    public static String stripTags(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        // First remove script and style blocks completely including their content
        String cleaned = SCRIPT_BLOCK.matcher(html).replaceAll("");
        cleaned = STYLE_BLOCK.matcher(cleaned).replaceAll("");
        cleaned = HTML_COMMENT.matcher(cleaned).replaceAll("");
        cleaned = ALL_HTML_TAGS.matcher(cleaned).replaceAll("");
        return cleaned;
    }

    /**
     * Sanitizes HTML by stripping dangerous executable tags, event handlers, and javascript: protocols,
     * while preserving benign structural HTML tags (e.g. &lt;p&gt;, &lt;b&gt;, &lt;a href="..."&gt;, &lt;div&gt;).
     *
     * @param html raw HTML string
     * @return sanitized HTML string
     */
    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        // 1. Remove dangerous tag blocks
        String result = SCRIPT_BLOCK.matcher(html).replaceAll("");
        result = STYLE_BLOCK.matcher(result).replaceAll("");
        result = IFRAME_BLOCK.matcher(result).replaceAll("");
        result = OBJECT_BLOCK.matcher(result).replaceAll("");
        result = EMBED_TAG.matcher(result).replaceAll("");
        result = APPLET_BLOCK.matcher(result).replaceAll("");
        result = META_TAG.matcher(result).replaceAll("");
        result = LINK_TAG.matcher(result).replaceAll("");
        result = BASE_TAG.matcher(result).replaceAll("");
        result = SVG_BLOCK.matcher(result).replaceAll("");

        // 2. Remove dangerous event handlers (quoted and unquoted)
        result = ON_EVENT_QUOTED.matcher(result).replaceAll("");
        result = ON_EVENT_UNQUOTED.matcher(result).replaceAll("");

        // 3. Remove dangerous protocols (javascript:, vbscript:, data:)
        result = JAVASCRIPT_HREF_QUOTED.matcher(result).replaceAll("");
        result = JAVASCRIPT_HREF_UNQUOTED.matcher(result).replaceAll("");

        return result;
    }

    /**
     * Checks if the input string contains potentially dangerous XSS patterns
     * such as script tags, event handlers (onload, onerror), or javascript: links.
     *
     * @param html input string
     * @return true if suspicious XSS patterns are detected
     */
    public static boolean containsXss(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        return SUSPICIOUS_XSS.matcher(html).find();
    }
}
