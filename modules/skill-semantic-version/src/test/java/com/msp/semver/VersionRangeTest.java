package com.msp.semver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionRangeTest {

    @Test
    @DisplayName("Caret range tests (^1.2.3, ^0.2.3, ^0.0.3)")
    void caretRanges() {
        // ^1.2.3 := >=1.2.3 <2.0.0
        VersionRange r1 = VersionRange.parse("^1.2.3");
        assertTrue(r1.isSatisfiedBy("1.2.3"));
        assertTrue(r1.isSatisfiedBy("1.2.4"));
        assertTrue(r1.isSatisfiedBy("1.9.0"));
        assertFalse(r1.isSatisfiedBy("1.2.2"));
        assertFalse(r1.isSatisfiedBy("2.0.0"));

        // ^0.2.3 := >=0.2.3 <0.3.0
        VersionRange r2 = VersionRange.parse("^0.2.3");
        assertTrue(r2.isSatisfiedBy("0.2.3"));
        assertTrue(r2.isSatisfiedBy("0.2.9"));
        assertFalse(r2.isSatisfiedBy("0.3.0"));
        assertFalse(r2.isSatisfiedBy("0.2.2"));

        // ^0.0.3 := >=0.0.3 <0.0.4
        VersionRange r3 = VersionRange.parse("^0.0.3");
        assertTrue(r3.isSatisfiedBy("0.0.3"));
        assertFalse(r3.isSatisfiedBy("0.0.4"));
        assertFalse(r3.isSatisfiedBy("0.0.2"));
    }

    @Test
    @DisplayName("Tilde range tests (~1.2.3, ~1.2, ~1)")
    void tildeRanges() {
        // ~1.2.3 := >=1.2.3 <1.3.0
        VersionRange r1 = VersionRange.parse("~1.2.3");
        assertTrue(r1.isSatisfiedBy("1.2.3"));
        assertTrue(r1.isSatisfiedBy("1.2.9"));
        assertFalse(r1.isSatisfiedBy("1.3.0"));
        assertFalse(r1.isSatisfiedBy("1.2.2"));

        // ~1.2 := >=1.2.0 <1.3.0
        VersionRange r2 = VersionRange.parse("~1.2");
        assertTrue(r2.isSatisfiedBy("1.2.0"));
        assertTrue(r2.isSatisfiedBy("1.2.5"));
        assertFalse(r2.isSatisfiedBy("1.3.0"));

        // ~1 := >=1.0.0 <2.0.0
        VersionRange r3 = VersionRange.parse("~1");
        assertTrue(r3.isSatisfiedBy("1.0.0"));
        assertTrue(r3.isSatisfiedBy("1.9.9"));
        assertFalse(r3.isSatisfiedBy("2.0.0"));
    }

    @Test
    @DisplayName("Comparison operators and conjunctions (>=, <, <=, >)")
    void comparisonOperators() {
        assertTrue(VersionRange.satisfies(">=1.0.0 <2.0.0", "1.5.0"));
        assertFalse(VersionRange.satisfies(">=1.0.0 <2.0.0", "2.0.0"));
        assertFalse(VersionRange.satisfies(">=1.0.0 <2.0.0", "0.9.9"));

        assertTrue(VersionRange.satisfies("<=2.1.0", "2.1.0"));
        assertTrue(VersionRange.satisfies("<=2.1.0", "2.0.9"));
        assertFalse(VersionRange.satisfies("<=2.1.0", "2.1.1"));

        assertTrue(VersionRange.satisfies(">1.5.0", "1.5.1"));
        assertFalse(VersionRange.satisfies(">1.5.0", "1.5.0"));

        assertTrue(VersionRange.satisfies("=1.2.3", "1.2.3"));
        assertFalse(VersionRange.satisfies("=1.2.3", "1.2.4"));
    }

    @Test
    @DisplayName("Hyphen range tests (1.0.0 - 2.0.0)")
    void hyphenRanges() {
        VersionRange range = VersionRange.parse("1.0.0 - 2.0.0");
        assertTrue(range.isSatisfiedBy("1.0.0"));
        assertTrue(range.isSatisfiedBy("1.5.2"));
        assertTrue(range.isSatisfiedBy("2.0.0"));
        assertFalse(range.isSatisfiedBy("2.0.1"));
        assertFalse(range.isSatisfiedBy("0.9.9"));
    }

    @Test
    @DisplayName("Disjunction OR pipe tests (||)")
    void disjunctions() {
        VersionRange range = VersionRange.parse("^1.0.0 || ^3.0.0");
        assertTrue(range.isSatisfiedBy("1.2.0"));
        assertTrue(range.isSatisfiedBy("3.1.5"));
        assertFalse(range.isSatisfiedBy("2.0.0"));
        assertFalse(range.isSatisfiedBy("4.0.0"));
    }

    @Test
    @DisplayName("Wildcards (*, 1.x, 1.2.*)")
    void wildcards() {
        assertTrue(VersionRange.satisfies("*", "9.9.9"));
        assertTrue(VersionRange.satisfies("1.x", "1.5.3"));
        assertFalse(VersionRange.satisfies("1.x", "2.0.0"));

        assertTrue(VersionRange.satisfies("1.2.*", "1.2.9"));
        assertFalse(VersionRange.satisfies("1.2.*", "1.3.0"));
    }
}
