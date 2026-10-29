package com.msp.isbn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IsbnFormatterTest {

    @Test
    @DisplayName("Clean removes hyphens and spaces and uppercases X")
    void clean() {
        assertEquals("0306406152", IsbnFormatter.clean("0-306-40615-2"));
        assertEquals("080442957X", IsbnFormatter.clean(" 0-8044-2957-x "));
        assertEquals("9780306406157", IsbnFormatter.clean("978-0-306-40615-7"));
        assertEquals("", IsbnFormatter.clean(null));
    }

    @Test
    @DisplayName("Format ISBN-10 and ISBN-13")
    void formatting() {
        assertEquals("0-306-40615-2", IsbnFormatter.formatIsbn10("0306406152"));
        assertEquals("978-0-306-40615-7", IsbnFormatter.formatIsbn13("9780306406157"));

        // Malformed inputs return untouched
        assertEquals("123", IsbnFormatter.formatIsbn10("123"));
        assertEquals("123", IsbnFormatter.formatIsbn13("123"));
    }
}
