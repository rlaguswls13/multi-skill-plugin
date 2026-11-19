package com.msp.jarowinkler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Jaro and Jaro-Winkler Similarity Metric Tests")
class JaroWinklerTest {

    private static final double EPSILON = 0.001;

    @Test
    @DisplayName("Exact matches and null/empty boundary conditions")
    void testBoundaryConditions() {
        assertEquals(1.0, JaroWinkler.similarity("test", "test"), EPSILON);
        assertEquals(0.0, JaroWinkler.distance("test", "test"), EPSILON);

        assertEquals(1.0, JaroWinkler.similarity("", ""), EPSILON);
        assertEquals(1.0, JaroWinkler.similarity(null, null), EPSILON);

        assertEquals(0.0, JaroWinkler.similarity("test", ""), EPSILON);
        assertEquals(0.0, JaroWinkler.similarity("", "test"), EPSILON);
        assertEquals(0.0, JaroWinkler.similarity("test", null), EPSILON);
        assertEquals(0.0, JaroWinkler.similarity(null, "test"), EPSILON);

        assertEquals(0.0, JaroWinkler.similarity("abc", "xyz"), EPSILON);
    }

    @Test
    @DisplayName("Canonical literature test vectors: MARTHA vs MARHTA")
    void testMarthaVsMarhta() {
        double jaro = JaroWinkler.jaroSimilarity("MARTHA", "MARHTA");
        double winkler = JaroWinkler.similarity("MARTHA", "MARHTA");

        assertEquals(0.944, jaro, EPSILON);
        assertEquals(0.961, winkler, EPSILON);
        assertEquals(1.0 - winkler, JaroWinkler.distance("MARTHA", "MARHTA"), EPSILON);
    }

    @Test
    @DisplayName("Canonical literature test vectors: DWAYNE vs DUANE")
    void testDwayneVsDuane() {
        double jaro = JaroWinkler.jaroSimilarity("DWAYNE", "DUANE");
        double winkler = JaroWinkler.similarity("DWAYNE", "DUANE");

        assertEquals(0.822, jaro, EPSILON);
        assertEquals(0.840, winkler, EPSILON);
    }

    @Test
    @DisplayName("Canonical literature test vectors: DIXON vs DICKSONX")
    void testDixonVsDicksonx() {
        double jaro = JaroWinkler.jaroSimilarity("DIXON", "DICKSONX");
        double winkler = JaroWinkler.similarity("DIXON", "DICKSONX");

        assertEquals(0.767, jaro, EPSILON);
        assertEquals(0.813, winkler, EPSILON);
    }

    @Test
    @DisplayName("Canonical literature test vectors: CRATE vs TRACE (prefix is 0)")
    void testCrateVsTrace() {
        double jaro = JaroWinkler.jaroSimilarity("CRATE", "TRACE");
        double winkler = JaroWinkler.similarity("CRATE", "TRACE");

        assertEquals(0.733, jaro, EPSILON);
        assertEquals(jaro, winkler, EPSILON); // No prefix bonus
    }

    @Test
    @DisplayName("isMatch evaluates threshold comparisons accurately")
    void testIsMatchThreshold() {
        assertTrue(JaroWinkler.isMatch("MARTHA", "MARHTA", 0.95));
        assertFalse(JaroWinkler.isMatch("MARTHA", "MARHTA", 0.98));
        assertTrue(JaroWinkler.isMatch("hello", "hello", 1.0));
    }

    @Test
    @DisplayName("Case insensitive metric configuration works as expected")
    void testCaseInsensitiveMetric() {
        JaroWinklerMetric caseInsensitive = JaroWinkler.builder()
                .caseSensitive(false)
                .build();

        assertEquals(1.0, caseInsensitive.similarity("martha", "MARTHA"), EPSILON);
        assertEquals(0.961, caseInsensitive.similarity("martha", "MARHTA"), EPSILON);

        // Default is case-sensitive
        assertTrue(JaroWinkler.similarity("martha", "MARTHA") < 0.5);
    }

    @Test
    @DisplayName("Custom scaling factor and prefix length constraints")
    void testCustomParameters() {
        JaroWinklerMetric custom = JaroWinkler.builder()
                .scalingFactor(0.2)
                .maxPrefixLength(4)
                .boostThreshold(0.5)
                .build();

        double sim = custom.similarity("MARTHA", "MARHTA");
        // Prefix length 3, factor 0.2: 0.944 + 3 * 0.2 * (1 - 0.944) = 0.944 + 0.6 * 0.0555 ~ 0.977
        assertEquals(0.977, sim, EPSILON);

        // Validation error on illegal parameter product > 1.0
        assertThrows(IllegalArgumentException.class, () ->
                JaroWinkler.builder().scalingFactor(0.3).maxPrefixLength(4).build());
    }
}
