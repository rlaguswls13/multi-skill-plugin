package com.msp.htmlentity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HtmlEntityCoder Tests")
class HtmlEntityCoderTest {

    @Nested
    @DisplayName("Escape Tests")
    class EscapeTests {

        @Test
        @DisplayName("Escapes standard HTML reserved characters")
        void escapeReserved() {
            String input = "<div class=\"box\" id='main'>A & B</div>";
            String expected = "&lt;div class=&quot;box&quot; id=&apos;main&apos;&gt;A &amp; B&lt;/div&gt;";
            assertEquals(expected, HtmlEntityCoder.escape(input));
        }

        @Test
        @DisplayName("Returns same string when no escape is required (zero allocation)")
        void noEscapeNeeded() {
            String plain = "Hello World 123!";
            assertSame(plain, HtmlEntityCoder.escape(plain));
        }

        @Test
        @DisplayName("Handles null and empty strings gracefully")
        void nullAndEmpty() {
            assertNull(HtmlEntityCoder.escape(null));
            assertEquals("", HtmlEntityCoder.escape(""));
        }
    }

    @Nested
    @DisplayName("Unescape Tests")
    class UnescapeTests {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = '|', value = {
                "&amp; | &",
                "&lt; | <",
                "&gt; | >",
                "&quot; | \"",
                "&copy; | ©",
                "&reg; | ®",
                "&trade; | ™",
                "&euro; | €",
                "&nbsp; | \u00A0",
                "&alpha;&beta;&gamma; | αβγ"
        })
        @DisplayName("Unescapes named entities")
        void unescapeNamed(String encoded, String decoded) {
            assertEquals(decoded, HtmlEntityCoder.unescape(encoded));
        }

        @Test
        @DisplayName("Unescapes apostrophe named and numeric entities")
        void unescapeApostrophe() {
            assertEquals("'", HtmlEntityCoder.unescape("&apos;"));
            assertEquals("'", HtmlEntityCoder.unescape("&#39;"));
            assertEquals("'", HtmlEntityCoder.unescape("&#x27;"));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = '|', value = {
                "&#169; | ©",
                "&#60; | <",
                "&#xA9; | ©",
                "&#X20AC; | €",
                "&#x3C; | <"
        })
        @DisplayName("Unescapes numerical decimal and hexadecimal entities")
        void unescapeNumeric(String encoded, String decoded) {
            assertEquals(decoded, HtmlEntityCoder.unescape(encoded));
        }

        @Test
        @DisplayName("Unescapes astral Unicode code points (surrogate pairs like emojis)")
        void unescapeEmoji() {
            // U+1F600 = Grinning Face 😀
            assertEquals("😀", HtmlEntityCoder.unescape("&#x1F600;"));
            assertEquals("😀", HtmlEntityCoder.unescape("&#128512;"));
        }

        @Test
        @DisplayName("Leaves unknown or malformed entities intact")
        void malformedEntities() {
            assertEquals("&unknown;", HtmlEntityCoder.unescape("&unknown;"));
            assertEquals("&", HtmlEntityCoder.unescape("&"));
            assertEquals("foo & bar", HtmlEntityCoder.unescape("foo & bar"));
            assertEquals("&#9999999999999999999999;", HtmlEntityCoder.unescape("&#9999999999999999999999;"));
            assertEquals("&#xZZZ;", HtmlEntityCoder.unescape("&#xZZZ;"));
        }

        @Test
        @DisplayName("Handles null and empty strings gracefully")
        void nullAndEmpty() {
            assertNull(HtmlEntityCoder.unescape(null));
            assertEquals("", HtmlEntityCoder.unescape(""));
            String plain = "No entities here";
            assertSame(plain, HtmlEntityCoder.unescape(plain));
        }
    }

    @Nested
    @DisplayName("Round Trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("unescape(escape(text)) recovers original text")
        void roundTrip() {
            String original = "<p>Title: \"Rock & Roll\", 10 > 5 and 3 < 4, it's nice!</p>";
            String escaped = HtmlEntityCoder.escape(original);
            String unescaped = HtmlEntityCoder.unescape(escaped);
            assertEquals(original, unescaped);
        }
    }
}
