package com.msp.semver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemVerTest {

    @Test
    @DisplayName("Parse valid SemVer 2.0.0 strings")
    void parseValid() {
        SemVer v1 = SemVer.parse("1.2.3");
        assertEquals(1, v1.getMajor());
        assertEquals(2, v1.getMinor());
        assertEquals(3, v1.getPatch());
        assertTrue(v1.getPreRelease().isEmpty());
        assertTrue(v1.getBuildMetadata().isEmpty());
        assertTrue(v1.isStable());

        SemVer v2 = SemVer.parse("v2.0.1-rc.1+build.123");
        assertEquals(2, v2.getMajor());
        assertEquals(0, v2.getMinor());
        assertEquals(1, v2.getPatch());
        assertEquals("rc.1", v2.getPreRelease().orElseThrow());
        assertEquals("build.123", v2.getBuildMetadata().orElseThrow());
        assertTrue(v2.isPreRelease());
        assertFalse(v2.isStable());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-0.3.7",
            "1.0.0-x.7.z.92",
            "1.0.0-x-y-z.-",
            "1.0.0+20130313144700",
            "1.0.0-beta+exp.sha.5114f85"
    })
    @DisplayName("Parse standard SemVer examples")
    void standardExamples(String ver) {
        SemVer parsed = SemVer.parse(ver);
        assertNotNull(parsed);
    }

    @Test
    @DisplayName("Precedence order matches official SemVer 2.0.0 specification")
    void precedenceOrder() {
        // Spec 11: 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0
        List<String> specSequence = List.of(
                "1.0.0-alpha",
                "1.0.0-alpha.1",
                "1.0.0-alpha.beta",
                "1.0.0-beta",
                "1.0.0-beta.2",
                "1.0.0-beta.11",
                "1.0.0-rc.1",
                "1.0.0"
        );

        for (int i = 0; i < specSequence.size() - 1; i++) {
            SemVer v1 = SemVer.parse(specSequence.get(i));
            SemVer v2 = SemVer.parse(specSequence.get(i + 1));
            assertTrue(v1.compareTo(v2) < 0, v1 + " should be less than " + v2);
            assertTrue(v2.compareTo(v1) > 0, v2 + " should be greater than " + v1);
            assertTrue(v1.isLessThan(v2));
            assertTrue(v2.isGreaterThan(v1));
        }
    }

    @Test
    @DisplayName("Build metadata is ignored in precedence comparison")
    void buildMetadataIgnored() {
        SemVer v1 = SemVer.parse("1.0.0+20130313144700");
        SemVer v2 = SemVer.parse("1.0.0+exp.sha.5114f85");

        assertEquals(0, v1.compareTo(v2));
        assertTrue(v1.isEquivalentTo(v2));
        assertFalse(v1.equals(v2)); // equals checks exact fields including build metadata
    }

    @Test
    @DisplayName("Version bumping")
    void bumping() {
        SemVer original = SemVer.parse("1.2.3-beta.1+build.42");

        SemVer major = original.bumpMajor();
        assertEquals("2.0.0", major.toString());

        SemVer minor = original.bumpMinor();
        assertEquals("1.3.0", minor.toString());

        SemVer patch = original.bumpPatch();
        assertEquals("1.2.4", patch.toString());

        SemVer withPre = major.withPreRelease("alpha.1");
        assertEquals("2.0.0-alpha.1", withPre.toString());
    }

    @Test
    @DisplayName("Lenient parsing")
    void lenientParsing() {
        assertEquals("1.0.0", SemVer.parseLenient("1").toString());
        assertEquals("1.2.0", SemVer.parseLenient("v1.2").toString());
        assertEquals("2.3.4", SemVer.parseLenient("2.3.4").toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid",
            "1.2.3.4",
            "01.1.1", // leading zero in major per spec
            "1.02.1",
            "1.1.03",
            "1.0.0-"
    })
    @DisplayName("Strict parse rejects invalid SemVer strings")
    void invalidSemVer(String invalid) {
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse(invalid));
    }
}
