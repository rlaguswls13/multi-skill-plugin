package com.msp.htmlentity;

import java.util.HashMap;
import java.util.Map;

/**
 * High-performance, zero-dependency HTML escape and unescape utility.
 * <p>
 * Supports standard HTML escape characters (&amp;, &lt;, &gt;, &quot;, &apos;),
 * named character references (&amp;copy;, &amp;nbsp;, &amp;reg;, etc.),
 * and numerical character references (decimal &amp;#169; and hexadecimal &amp;#xA9;).
 */
public final class HtmlEntityCoder {

    private static final Map<String, String> NAMED_ENTITIES = new HashMap<>(256);

    static {
        // Basic XML / HTML predefined entities
        NAMED_ENTITIES.put("amp", "&");
        NAMED_ENTITIES.put("lt", "<");
        NAMED_ENTITIES.put("gt", ">");
        NAMED_ENTITIES.put("quot", "\"");
        NAMED_ENTITIES.put("apos", "'");

        // Common symbols and punctuation
        NAMED_ENTITIES.put("nbsp", "\u00A0");
        NAMED_ENTITIES.put("copy", "\u00A9");
        NAMED_ENTITIES.put("reg", "\u00AE");
        NAMED_ENTITIES.put("trade", "\u2122");
        NAMED_ENTITIES.put("cent", "\u00A2");
        NAMED_ENTITIES.put("pound", "\u00A3");
        NAMED_ENTITIES.put("euro", "\u20AC");
        NAMED_ENTITIES.put("yen", "\u00A5");
        NAMED_ENTITIES.put("sect", "\u00A7");
        NAMED_ENTITIES.put("deg", "\u00B0");
        NAMED_ENTITIES.put("plusmn", "\u00B1");
        NAMED_ENTITIES.put("para", "\u00B6");
        NAMED_ENTITIES.put("middot", "\u00B7");
        NAMED_ENTITIES.put("times", "\u00D7");
        NAMED_ENTITIES.put("divide", "\u00F7");
        NAMED_ENTITIES.put("frac14", "\u00BC");
        NAMED_ENTITIES.put("frac12", "\u00BD");
        NAMED_ENTITIES.put("frac34", "\u00BE");
        NAMED_ENTITIES.put("laquo", "\u00AB");
        NAMED_ENTITIES.put("raquo", "\u00BB");
        NAMED_ENTITIES.put("bull", "\u2022");
        NAMED_ENTITIES.put("hellip", "\u2026");
        NAMED_ENTITIES.put("ndash", "\u2013");
        NAMED_ENTITIES.put("mdash", "\u2014");
        NAMED_ENTITIES.put("lsquo", "\u2018");
        NAMED_ENTITIES.put("rsquo", "\u2019");
        NAMED_ENTITIES.put("sbquo", "\u201A");
        NAMED_ENTITIES.put("ldquo", "\u201C");
        NAMED_ENTITIES.put("rdquo", "\u201D");
        NAMED_ENTITIES.put("bdquo", "\u201E");
        NAMED_ENTITIES.put("dagger", "\u2020");
        NAMED_ENTITIES.put("Dagger", "\u2021");
        NAMED_ENTITIES.put("permil", "\u2030");
        NAMED_ENTITIES.put("lsaquo", "\u2039");
        NAMED_ENTITIES.put("rsaquo", "\u203A");

        // Mathematical & arrow symbols
        NAMED_ENTITIES.put("infin", "\u221E");
        NAMED_ENTITIES.put("ne", "\u2260");
        NAMED_ENTITIES.put("le", "\u2264");
        NAMED_ENTITIES.put("ge", "\u2265");
        NAMED_ENTITIES.put("sum", "\u2211");
        NAMED_ENTITIES.put("prod", "\u220F");
        NAMED_ENTITIES.put("radic", "\u221A");
        NAMED_ENTITIES.put("minus", "\u2212");
        NAMED_ENTITIES.put("empty", "\u2205");
        NAMED_ENTITIES.put("isin", "\u2208");
        NAMED_ENTITIES.put("notin", "\u2209");
        NAMED_ENTITIES.put("forall", "\u2200");
        NAMED_ENTITIES.put("exist", "\u2203");
        NAMED_ENTITIES.put("sub", "\u2282");
        NAMED_ENTITIES.put("sup", "\u2283");
        NAMED_ENTITIES.put("sube", "\u2286");
        NAMED_ENTITIES.put("supe", "\u2287");
        NAMED_ENTITIES.put("larr", "\u2190");
        NAMED_ENTITIES.put("uarr", "\u2191");
        NAMED_ENTITIES.put("rarr", "\u2192");
        NAMED_ENTITIES.put("darr", "\u2193");
        NAMED_ENTITIES.put("harr", "\u2194");
        NAMED_ENTITIES.put("spades", "\u2660");
        NAMED_ENTITIES.put("clubs", "\u2663");
        NAMED_ENTITIES.put("hearts", "\u2665");
        NAMED_ENTITIES.put("diams", "\u2666");

        // Greek letters
        NAMED_ENTITIES.put("alpha", "\u03B1");
        NAMED_ENTITIES.put("beta", "\u03B2");
        NAMED_ENTITIES.put("gamma", "\u03B3");
        NAMED_ENTITIES.put("delta", "\u03B4");
        NAMED_ENTITIES.put("epsilon", "\u03B5");
        NAMED_ENTITIES.put("zeta", "\u03B6");
        NAMED_ENTITIES.put("eta", "\u03B7");
        NAMED_ENTITIES.put("theta", "\u03B8");
        NAMED_ENTITIES.put("iota", "\u03B9");
        NAMED_ENTITIES.put("kappa", "\u03BA");
        NAMED_ENTITIES.put("lambda", "\u03BB");
        NAMED_ENTITIES.put("mu", "\u03BC");
        NAMED_ENTITIES.put("nu", "\u03BD");
        NAMED_ENTITIES.put("xi", "\u03BE");
        NAMED_ENTITIES.put("pi", "\u03C0");
        NAMED_ENTITIES.put("rho", "\u03C1");
        NAMED_ENTITIES.put("sigma", "\u03C3");
        NAMED_ENTITIES.put("tau", "\u03C4");
        NAMED_ENTITIES.put("phi", "\u03C6");
        NAMED_ENTITIES.put("chi", "\u03C7");
        NAMED_ENTITIES.put("psi", "\u03C8");
        NAMED_ENTITIES.put("omega", "\u03C9");
        NAMED_ENTITIES.put("Alpha", "\u0391");
        NAMED_ENTITIES.put("Beta", "\u0392");
        NAMED_ENTITIES.put("Gamma", "\u0393");
        NAMED_ENTITIES.put("Delta", "\u0394");
        NAMED_ENTITIES.put("Theta", "\u0398");
        NAMED_ENTITIES.put("Lambda", "\u039B");
        NAMED_ENTITIES.put("Sigma", "\u03A3");
        NAMED_ENTITIES.put("Omega", "\u03A9");

        // Latin-1 characters
        NAMED_ENTITIES.put("agrave", "\u00E0");
        NAMED_ENTITIES.put("aacute", "\u00E1");
        NAMED_ENTITIES.put("acirc", "\u00E2");
        NAMED_ENTITIES.put("atilde", "\u00E3");
        NAMED_ENTITIES.put("auml", "\u00E4");
        NAMED_ENTITIES.put("aring", "\u00E5");
        NAMED_ENTITIES.put("aelig", "\u00E6");
        NAMED_ENTITIES.put("ccedil", "\u00E7");
        NAMED_ENTITIES.put("egrave", "\u00E8");
        NAMED_ENTITIES.put("eacute", "\u00E9");
        NAMED_ENTITIES.put("ecirc", "\u00EA");
        NAMED_ENTITIES.put("euml", "\u00EB");
        NAMED_ENTITIES.put("igrave", "\u00EC");
        NAMED_ENTITIES.put("iacute", "\u00ED");
        NAMED_ENTITIES.put("icirc", "\u00EE");
        NAMED_ENTITIES.put("iuml", "\u00EF");
        NAMED_ENTITIES.put("ntilde", "\u00F1");
        NAMED_ENTITIES.put("ograve", "\u00F2");
        NAMED_ENTITIES.put("oacute", "\u00F3");
        NAMED_ENTITIES.put("ocirc", "\u00F4");
        NAMED_ENTITIES.put("otilde", "\u00F5");
        NAMED_ENTITIES.put("ouml", "\u00F6");
        NAMED_ENTITIES.put("ugrave", "\u00F9");
        NAMED_ENTITIES.put("uacute", "\u00FA");
        NAMED_ENTITIES.put("ucirc", "\u00FB");
        NAMED_ENTITIES.put("uuml", "\u00FC");
        NAMED_ENTITIES.put("szlig", "\u00DF");
    }

    private HtmlEntityCoder() {
        // Utility class
    }

    /**
     * Escapes critical HTML reserved characters (&amp;, &lt;, &gt;, &quot;, &apos;).
     *
     * @param html raw HTML string
     * @return safely escaped HTML string, or original instance if no escapes needed
     */
    public static String escape(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        // Fast zero-allocation scan
        boolean needsEscape = false;
        int len = html.length();
        for (int i = 0; i < len; i++) {
            char c = html.charAt(i);
            if (c == '&' || c == '<' || c == '>' || c == '"' || c == '\'') {
                needsEscape = true;
                break;
            }
        }

        if (!needsEscape) {
            return html;
        }

        StringBuilder sb = new StringBuilder(len + 16);
        for (int i = 0; i < len; i++) {
            char c = html.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Unescapes HTML entities (named, decimal, and hexadecimal) back into characters.
     *
     * @param text HTML string containing entities
     * @return unescaped decoded text, or original instance if no entities present
     */
    public static String unescape(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Fast check if '&' exists
        int firstAmp = text.indexOf('&');
        if (firstAmp == -1) {
            return text;
        }

        int len = text.length();
        StringBuilder sb = new StringBuilder(len);
        // Append prefix prior to first '&'
        sb.append(text, 0, firstAmp);

        int i = firstAmp;
        while (i < len) {
            char c = text.charAt(i);
            if (c == '&') {
                int semi = text.indexOf(';', i + 1);
                if (semi != -1 && (semi - i) <= 32) {
                    String entity = text.substring(i + 1, semi);
                    if (isValidEntityContent(entity)) {
                        String decoded = resolveEntity(entity);
                        if (decoded != null) {
                            sb.append(decoded);
                            i = semi + 1;
                            continue;
                        }
                    }
                }
            }
            sb.append(c);
            i++;
        }

        return sb.toString();
    }

    private static boolean isValidEntityContent(String entity) {
        if (entity.isEmpty()) {
            return false;
        }
        for (int i = 0; i < entity.length(); i++) {
            char ch = entity.charAt(i);
            if (Character.isWhitespace(ch)) {
                return false;
            }
        }
        return true;
    }

    private static String resolveEntity(String entity) {
        if (entity.startsWith("#")) {
            if (entity.length() <= 1) {
                return null;
            }
            char second = entity.charAt(1);
            if (second == 'x' || second == 'X') {
                if (entity.length() <= 2) {
                    return null;
                }
                try {
                    int codePoint = Integer.parseInt(entity.substring(2), 16);
                    if (Character.isValidCodePoint(codePoint)) {
                        return new String(Character.toChars(codePoint));
                    }
                } catch (NumberFormatException ignored) {
                    return null;
                }
            } else {
                try {
                    int codePoint = Integer.parseInt(entity.substring(1), 10);
                    if (Character.isValidCodePoint(codePoint)) {
                        return new String(Character.toChars(codePoint));
                    }
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }

        return NAMED_ENTITIES.get(entity);
    }
}
