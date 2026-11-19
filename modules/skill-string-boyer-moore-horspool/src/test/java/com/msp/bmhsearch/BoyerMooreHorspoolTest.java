package com.msp.bmhsearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoyerMooreHorspoolTest {

    @Test
    @DisplayName("Exact substring search at start, middle, and end")
    void testBasicStringSearch() {
        String text = "The quick brown fox jumps over the lazy dog";

        assertEquals(0, BoyerMooreHorspool.indexOf(text, "The"));
        assertEquals(4, BoyerMooreHorspool.indexOf(text, "quick"));
        assertEquals(16, BoyerMooreHorspool.indexOf(text, "fox"));
        assertEquals(40, BoyerMooreHorspool.indexOf(text, "dog"));
        assertEquals(-1, BoyerMooreHorspool.indexOf(text, "cat"));
    }

    @Test
    @DisplayName("Case-insensitive substring search")
    void testCaseInsensitiveSearch() {
        String text = "Java is an Object-Oriented Programming Language";

        assertEquals(0, BoyerMooreHorspool.indexOfIgnoreCase(text, "JAVA"));
        assertEquals(11, BoyerMooreHorspool.indexOfIgnoreCase(text, "object-ORIENTED"));
        assertEquals(39, BoyerMooreHorspool.indexOfIgnoreCase(text, "LaNgUaGe"));
        assertEquals(-1, BoyerMooreHorspool.indexOfIgnoreCase(text, "python"));
    }

    @Test
    @DisplayName("Find all occurrences of a pattern")
    void testFindAll() {
        String text = "banana bananarama bandana banana";
        List<Integer> matches = BoyerMooreHorspool.findAll(text, "banana");

        assertEquals(3, matches.size());
        assertEquals(0, matches.get(0));
        assertEquals(7, matches.get(1));
        assertEquals(26, matches.get(2));
    }

    @Test
    @DisplayName("Find all case-insensitive occurrences")
    void testFindAllIgnoreCase() {
        String text = "Apple apple APPLE aPple";
        List<Integer> matches = BoyerMooreHorspool.findAllIgnoreCase(text, "apple");

        assertEquals(4, matches.size());
        assertEquals(List.of(0, 6, 12, 18), matches);
    }

    @Test
    @DisplayName("Boundary conditions: empty pattern, empty text, pattern longer than text")
    void testBoundaries() {
        assertEquals(0, BoyerMooreHorspool.indexOf("hello", ""));
        assertEquals(2, BoyerMooreHorspool.indexOf("hello", "", 2));
        assertEquals(-1, BoyerMooreHorspool.indexOf("", "hello"));
        assertEquals(-1, BoyerMooreHorspool.indexOf("hi", "hello"));
        assertEquals(-1, BoyerMooreHorspool.indexOf("hello", "hello", 1));
        assertEquals(0, BoyerMooreHorspool.indexOf("hello", "hello", 0));
    }

    @Test
    @DisplayName("Byte array search with magic bytes and partial matches")
    void testByteArraySearch() {
        // PNG magic header: 89 50 4E 47 0D 0A 1A 0A
        byte[] data = new byte[]{
            0x00, 0x11, 0x22,
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x33, 0x44
        };
        byte[] magic = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};

        int index = BoyerMooreHorspool.indexOf(data, magic);
        assertEquals(3, index);

        // Not found
        byte[] notFound = new byte[]{(byte) 0xFF, (byte) 0xFE};
        assertEquals(-1, BoyerMooreHorspool.indexOf(data, notFound));

        // Find all in byte array
        byte[] repeated = new byte[]{1, 2, 3, 9, 9, 1, 2, 3, 8, 1, 2, 3};
        byte[] target = new byte[]{1, 2, 3};
        List<Integer> occurrences = BoyerMooreHorspool.findAll(repeated, target);
        assertEquals(List.of(0, 5, 9), occurrences);
    }

    @Test
    @DisplayName("Unicode and Korean characters search")
    void testUnicodeSearch() {
        String text = "안녕하세요 세계! 모듈러 자바 21 플러그인 🚀";

        assertEquals(0, BoyerMooreHorspool.indexOf(text, "안녕하세요"));
        assertEquals(6, BoyerMooreHorspool.indexOf(text, "세계!"));
        assertEquals(10, BoyerMooreHorspool.indexOf(text, "모듈러"));
        assertTrue(BoyerMooreHorspool.indexOf(text, "🚀") > 0);
        assertEquals(-1, BoyerMooreHorspool.indexOf(text, "파이썬"));
    }

    @Test
    @DisplayName("Overlapping repeating character pattern")
    void testOverlappingRepeatedPattern() {
        String text = "aaaaa";
        List<Integer> matches = BoyerMooreHorspool.findAll(text, "aa");
        // Non-jumping / stepping fromIndex = found + 1 gives indices 0, 1, 2, 3
        assertEquals(List.of(0, 1, 2, 3), matches);
    }
}
