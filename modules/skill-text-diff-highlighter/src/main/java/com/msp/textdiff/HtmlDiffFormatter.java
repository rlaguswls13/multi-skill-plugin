package com.msp.textdiff;

import java.util.List;

/**
 * Renders diff output into semantic, accessible HTML using &lt;ins&gt; and &lt;del&gt; elements.
 * Groups consecutive changes into single tags for clean presentation.
 */
public final class HtmlDiffFormatter {

    private HtmlDiffFormatter() {
        // Utility class
    }

    /**
     * Formats line differences into an HTML container with line numbers and CSS classes.
     *
     * @param lineDiffs List of line diffs.
     * @return HTML snippet string.
     */
    public static String formatLines(List<LineDiff> lineDiffs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"diff-view\">").append(System.lineSeparator());

        for (LineDiff diff : lineDiffs) {
            String cssClass = switch (diff.type()) {
                case EQUAL -> "diff-equal";
                case DELETE -> "diff-delete";
                case INSERT -> "diff-insert";
            };

            String prefix = switch (diff.type()) {
                case EQUAL -> " ";
                case DELETE -> "-";
                case INSERT -> "+";
            };

            int lineNum = (diff.type() == DiffType.INSERT) ? diff.newLineNumber() : diff.oldLineNumber();

            sb.append("  <div class=\"diff-line ").append(cssClass).append("\">")
              .append("<span class=\"line-num\">").append(lineNum).append("</span> ")
              .append("<span class=\"prefix\">").append(prefix).append("</span> ")
              .append("<span class=\"content\">").append(escapeHtml(diff.line())).append("</span>")
              .append("</div>").append(System.lineSeparator());
        }

        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Formats word/token differences inline using standard &lt;ins&gt; and &lt;del&gt; tags,
     * cleanly grouping adjacent tokens of the same type.
     *
     * @param wordDiffs List of word diffs.
     * @return HTML snippet with inline diff tags.
     */
    public static String formatWords(List<WordDiff> wordDiffs) {
        StringBuilder sb = new StringBuilder();
        DiffType currentType = null;
        StringBuilder currentBuffer = new StringBuilder();

        for (WordDiff diff : wordDiffs) {
            if (currentType == null) {
                currentType = diff.type();
                currentBuffer.append(diff.text());
            } else if (diff.type() == currentType) {
                currentBuffer.append(diff.text());
            } else {
                appendHtmlChunk(sb, currentType, currentBuffer.toString());
                currentType = diff.type();
                currentBuffer.setLength(0);
                currentBuffer.append(diff.text());
            }
        }
        if (currentType != null && !currentBuffer.isEmpty()) {
            appendHtmlChunk(sb, currentType, currentBuffer.toString());
        }
        return sb.toString();
    }

    private static void appendHtmlChunk(StringBuilder sb, DiffType type, String text) {
        String escaped = escapeHtml(text);
        switch (type) {
            case EQUAL -> sb.append(escaped);
            case DELETE -> sb.append("<del class=\"diff-del\">").append(escaped).append("</del>");
            case INSERT -> sb.append("<ins class=\"diff-ins\">").append(escaped).append("</ins>");
        }
    }

    /**
     * Escapes standard HTML special characters (&amp;, &lt;, &gt;, &quot;, &#39;).
     */
    public static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
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
}
