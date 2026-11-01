package com.msp.etag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HttpDateParser Unit Tests")
class HttpDateParserTest {

    @Test
    @DisplayName("Parses standard RFC 1123 date")
    void testParseRfc1123() {
        String rfc1123 = "Sun, 06 Nov 1994 08:49:37 GMT";
        Optional<Instant> instant = HttpDateParser.parse(rfc1123);
        assertTrue(instant.isPresent());

        String formatted = HttpDateParser.formatRfc1123(instant.get());
        assertEquals(rfc1123, formatted);
    }

    @Test
    @DisplayName("Parses obsolete RFC 850 date")
    void testParseRfc850() {
        String rfc850 = "Sunday, 06-Nov-94 08:49:37 GMT";
        Optional<Instant> instant = HttpDateParser.parse(rfc850);
        assertTrue(instant.isPresent());
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", HttpDateParser.formatRfc1123(instant.get()));
    }

    @Test
    @DisplayName("Parses obsolete asctime date")
    void testParseAsctime() {
        String asctime = "Sun Nov  6 08:49:37 1994";
        Optional<Instant> instant = HttpDateParser.parse(asctime);
        assertTrue(instant.isPresent());
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", HttpDateParser.formatRfc1123(instant.get()));
    }

    @Test
    @DisplayName("Handles invalid dates gracefully")
    void testInvalidDates() {
        assertTrue(HttpDateParser.parse(null).isEmpty());
        assertTrue(HttpDateParser.parse("").isEmpty());
        assertTrue(HttpDateParser.parse("not-a-date").isEmpty());
    }
}
