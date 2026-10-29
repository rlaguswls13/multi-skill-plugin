package com.msp.htmlstrip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlTagStripperTest {

    @Test
    @DisplayName("Strips script and style tags along with their entire content")
    void testScriptAndStyleRemoval() {
        String html = """
                <html>
                <head>
                    <style type="text/css">
                        body { color: red; font-size: 14px; }
                        h1 > p { margin: 0; }
                    </style>
                </head>
                <body>
                    <h1>Welcome</h1>
                    <script type="text/javascript">
                        var x = 10;
                        if (x < 20) { alert('test'); }
                    </script>
                    <p>Safe Article Content</p>
                </body>
                </html>
                """;

        String result = HtmlTagStripper.strip(html);
        assertFalse(result.contains("body { color: red;"));
        assertFalse(result.contains("var x = 10;"));
        assertFalse(result.contains("alert"));
        assertTrue(result.contains("Welcome"));
        assertTrue(result.contains("Safe Article Content"));
    }

    @Test
    @DisplayName("Separates block elements with newlines so words do not stick together")
    void testBlockTagSpacing() {
        String html = "<p>First Paragraph</p><p>Second Paragraph</p><div>Third Item</div>";
        String result = HtmlTagStripper.strip(html);

        assertEquals("First Paragraph\nSecond Paragraph\nThird Item", result);
    }

    @Test
    @DisplayName("Single line stripping mode collapses all whitespace into single spaces")
    void testSingleLineStrip() {
        String html = "<p>Hello</p>\n\n<p>World</p> <div>Java &amp; 21</div>";
        String result = HtmlTagStripper.stripToSingleLine(html);

        assertEquals("Hello World Java & 21", result);
    }

    @Test
    @DisplayName("Unescapes named, decimal, and hexadecimal HTML entities")
    void testEntityUnescaping() {
        String html = "Price: &euro;100 &amp; &pound;50 &bull; Copyright &copy; 2026 &#39;Test&#39; &#x2F; &#x20AC;10";
        String result = HtmlTagStripper.strip(html);

        assertEquals("Price: €100 & £50 • Copyright © 2026 'Test' / €10", result);
    }

    @Test
    @DisplayName("Handles nested tags and inline formatting tags")
    void testNestedTags() {
        String html = "<article><div class=\"content\"><p>This is <b>bold</b> and <i>italic</i> and <a href=\"https://example.com\">link</a>.</p></div></article>";
        String result = HtmlTagStripper.strip(html);

        assertEquals("This is bold and italic and link.", result);
    }

    @Test
    @DisplayName("Removes HTML comments")
    void testCommentRemoval() {
        String html = "<!-- Comment header -->Visible Text<!-- Inline comment -->";
        String result = HtmlTagStripper.strip(html);

        assertEquals("Visible Text", result);
    }

    @Test
    @DisplayName("Handles attributes with > inside quotes safely")
    void testAttributeWithGreaterThanInsideQuote() {
        String html = "<input type=\"text\" value=\"condition: a > b and c < d\" readonly>Actual Text";
        String result = HtmlTagStripper.strip(html);

        assertEquals("Actual Text", result);
    }

    @Test
    @DisplayName("Preserves mathematical comparison operators that are not tags")
    void testMathOperatorsNotTreatedAsTags() {
        String text = "Formula: 3 < 5 and 7 > 2 is true.";
        String result = HtmlTagStripper.strip(text);

        assertEquals("Formula: 3 < 5 and 7 > 2 is true.", result);
    }

    @Test
    @DisplayName("Handles unclosed tags and malformed HTML gracefully")
    void testMalformedHtml() {
        String html = "<div><p>Unclosed paragraph <b>bold without close";
        String result = HtmlTagStripper.strip(html);

        assertEquals("Unclosed paragraph bold without close", result);
    }

    @Test
    @DisplayName("Handles null or empty string input safely")
    void testNullAndEmpty() {
        assertEquals("", HtmlTagStripper.strip(null));
        assertEquals("", HtmlTagStripper.strip(""));
        assertEquals("", HtmlTagStripper.strip("   \n\t  "));
    }
}
