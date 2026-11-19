package com.msp.suffixarray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuffixArrayTest {

    @Test
    @DisplayName("Classic 'banana' suffix array, Kasai LCP, distinct substrings, and longest repeated substring")
    void classicBananaExample() {
        SuffixArray sa = SuffixArray.build("banana");

        // Suffixes sorted: 5:"a", 3:"ana", 1:"anana", 0:"banana", 4:"na", 2:"nana"
        assertArrayEquals(new int[]{5, 3, 1, 0, 4, 2}, sa.suffixArray());
        assertArrayEquals(new int[]{0, 1, 3, 0, 0, 2}, sa.lcpArray());
        assertEquals("ana", sa.longestRepeatedSubstring());
        assertEquals(List.of("ana"), sa.longestRepeatedSubstrings());
        assertEquals(15L, sa.countDistinctSubstrings());

        assertEquals(3, sa.countOccurrences("a"));
        assertEquals(List.of(1, 3, 5), sa.findOccurrences("a"));
        assertEquals(2, sa.countOccurrences("ana"));
        assertEquals(List.of(1, 3), sa.findOccurrences("ana"));
        assertEquals(1, sa.countOccurrences("banana"));
        assertEquals(0, sa.countOccurrences("bananas"));
        assertFalse(sa.contains("xyz"));
        assertTrue(sa.contains("nan"));
    }

    @Test
    @DisplayName("Boundary cases: empty string, single char, all identical chars, all distinct chars")
    void boundaryCases() {
        SuffixArray empty = SuffixArray.build("");
        assertEquals(0, empty.length());
        assertArrayEquals(new int[0], empty.suffixArray());
        assertArrayEquals(new int[0], empty.lcpArray());
        assertEquals(0L, empty.countDistinctSubstrings());
        assertEquals("", empty.longestRepeatedSubstring());
        assertEquals(0, empty.countOccurrences("a"));

        SuffixArray single = SuffixArray.build("z");
        assertArrayEquals(new int[]{0}, single.suffixArray());
        assertArrayEquals(new int[]{0}, single.lcpArray());
        assertEquals(1L, single.countDistinctSubstrings());
        assertEquals("", single.longestRepeatedSubstring());
        assertEquals(List.of(0), single.findOccurrences("z"));

        SuffixArray allSame = SuffixArray.build("aaaaa");
        assertArrayEquals(new int[]{4, 3, 2, 1, 0}, allSame.suffixArray());
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, allSame.lcpArray());
        assertEquals("aaaa", allSame.longestRepeatedSubstring());
        assertEquals(5L, allSame.countDistinctSubstrings());
        assertEquals(List.of(0, 1, 2, 3), allSame.findOccurrences("aa"));

        SuffixArray allDistinct = SuffixArray.build("abcdef");
        assertEquals("", allDistinct.longestRepeatedSubstring());
        assertEquals(List.of(), allDistinct.longestRepeatedSubstrings());
        assertEquals(21L, allDistinct.countDistinctSubstrings());
    }

    @Test
    @DisplayName("Tied longest repeated substrings are returned in lexicographical order")
    void tiedLongestRepeatedSubstrings() {
        SuffixArray sa = SuffixArray.build("abcXabcYdefZdef");
        assertEquals("abc", sa.longestRepeatedSubstring());
        assertEquals(List.of("abc", "def"), sa.longestRepeatedSubstrings());
    }

    @Test
    @DisplayName("Longest common substring works for overlapping, Unicode, identical, and disjoint strings")
    void longestCommonSubstring() {
        assertEquals("aba", SuffixArray.longestCommonSubstring("xabax", "yabay"));
        assertEquals("_algorithm_", SuffixArray.longestCommonSubstring("fast_algorithm_v1", "my_algorithm_impl"));
        assertEquals("", SuffixArray.longestCommonSubstring("abc", "xyz"));
        assertEquals("", SuffixArray.longestCommonSubstring("", "abc"));
        assertEquals("hello", SuffixArray.longestCommonSubstring("hello", "hello"));
        assertEquals("가나다", SuffixArray.longestCommonSubstring("123가나다456", "789가나다000"));
    }

    @Test
    @DisplayName("Suffix array ordering and LCP match naive reference on complex strings")
    void crossValidateAgainstNaiveReference() {
        String[] samples = {
                "mississippi",
                "abracadabra",
                "aabaabaaaab",
                "한국어테스트한국어",
                "0100101001001010010"
        };
        for (String text : samples) {
            SuffixArray saObj = SuffixArray.build(text);
            int[] sa = saObj.suffixArray();
            int[] lcp = saObj.lcpArray();
            int n = text.length();
            assertEquals(n, sa.length);
            assertEquals(n, lcp.length);
            assertEquals(0, lcp[0]);
            for (int i = 1; i < n; i++) {
                String prev = text.substring(sa[i - 1]);
                String curr = text.substring(sa[i]);
                assertTrue(prev.compareTo(curr) < 0, "Suffixes must be strictly sorted for " + text);
                int expectedLcp = 0;
                int lim = Math.min(prev.length(), curr.length());
                while (expectedLcp < lim && prev.charAt(expectedLcp) == curr.charAt(expectedLcp)) {
                    expectedLcp++;
                }
                assertEquals(expectedLcp, lcp[i], "LCP mismatch at index " + i + " for " + text);
            }
        }
    }

    @Test
    @DisplayName("Empty pattern returns 0 occurrences and defensive copies protect internal arrays")
    void emptyPatternAndImmutability() {
        SuffixArray sa = SuffixArray.build("banana");
        assertEquals(0, sa.countOccurrences(""));
        assertEquals(List.of(), sa.findOccurrences(""));

        int[] copy = sa.suffixArray();
        copy[0] = -999;
        assertEquals(5, sa.suffixArray()[0]);

        assertThrows(NullPointerException.class, () -> SuffixArray.build(null));
        assertThrows(NullPointerException.class, () -> sa.countOccurrences(null));
    }
}
