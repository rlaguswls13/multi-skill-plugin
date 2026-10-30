package com.msp.semvercomp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StrictSemVer 2.0.0 Specification Tests")
class StrictSemVerTest {

    @Nested
    @DisplayName("Parsing & Validation")
    class ParsingTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "0.0.4",
                "1.2.3",
                "10.20.30",
                "1.0.0-alpha",
                "1.0.0-alpha.1",
                "1.0.0-0.3.7",
                "1.0.0-x.7.z.92",
                "1.0.0-x-y-z.-",
                "1.0.0-alpha+001",
                "1.0.0+20130313144700",
                "1.0.0-beta+exp.sha.5114f85",
                "1.0.0+21AF26D3----117B344092BD"
        })
        @DisplayName("Valid SemVer strings parse successfully")
        void validVersions(String version) {
            StrictSemVer semver = StrictSemVer.parse(version);
            assertNotNull(semver);
            assertEquals(version, semver.raw());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "1",
                "1.2",
                "1.2.3-01", // leading zero in numeric prerelease
                "01.1.1",    // leading zero in major
                "1.01.1",    // leading zero in minor
                "1.1.01",    // leading zero in patch
                "1.2.3.4",
                "v1.2.3",
                "1.2.3-",
                "1.2.3+",
                "1.2.3-alpha..1",
                "1.2.3+build..1",
                "alpha"
        })
        @DisplayName("Invalid SemVer strings throw IllegalArgumentException")
        void invalidVersions(String version) {
            assertThrows(IllegalArgumentException.class, () -> StrictSemVer.parse(version));
            assertEquals(Optional.empty(), StrictSemVer.tryParse(version));
        }

        @Test
        @DisplayName("Null string throws NullPointerException")
        void nullVersionThrows() {
            assertThrows(NullPointerException.class, () -> StrictSemVer.parse(null));
            assertEquals(Optional.empty(), StrictSemVer.tryParse(null));
        }

        @Test
        @DisplayName("Direct field inspection works correctly")
        void fieldInspection() {
            StrictSemVer ver = StrictSemVer.parse("2.4.8-beta.2+build.999");
            assertEquals(2, ver.major());
            assertEquals(4, ver.minor());
            assertEquals(8, ver.patch());
            assertEquals(Optional.of("beta.2"), ver.prerelease());
            assertEquals(Optional.of("build.999"), ver.buildMetadata());
            assertTrue(ver.isPrerelease());
            assertTrue(ver.hasBuildMetadata());
            assertEquals("2.4.8", ver.normalVersion());
        }
    }

    @Nested
    @DisplayName("SemVer 2.0.0 Precedence Specification")
    class PrecedenceTests {

        @Test
        @DisplayName("Major, minor, patch precedence ordering")
        void majorMinorPatchPrecedence() {
            assertTrue(StrictSemVer.parse("1.0.0").compareTo(StrictSemVer.parse("2.0.0")) < 0);
            assertTrue(StrictSemVer.parse("2.0.0").compareTo(StrictSemVer.parse("2.1.0")) < 0);
            assertTrue(StrictSemVer.parse("2.1.0").compareTo(StrictSemVer.parse("2.1.1")) < 0);
        }

        @Test
        @DisplayName("Pre-release version has lower precedence than normal version")
        void preReleaseLowerThanNormal() {
            StrictSemVer pre = StrictSemVer.parse("1.0.0-alpha");
            StrictSemVer normal = StrictSemVer.parse("1.0.0");
            assertTrue(pre.compareTo(normal) < 0);
            assertTrue(normal.compareTo(pre) > 0);
            assertTrue(normal.isGreaterThan(pre));
            assertTrue(pre.isLessThan(normal));
        }

        @Test
        @DisplayName("SemVer 2.0 Spec example sequence must be strictly sorted")
        void semVerOfficialPrecedenceChain() {
            // 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0
            List<String> rawOrder = List.of(
                    "1.0.0-alpha",
                    "1.0.0-alpha.1",
                    "1.0.0-alpha.beta",
                    "1.0.0-beta",
                    "1.0.0-beta.2",
                    "1.0.0-beta.11",
                    "1.0.0-rc.1",
                    "1.0.0"
            );

            List<StrictSemVer> parsed = new ArrayList<>(rawOrder.stream().map(StrictSemVer::parse).toList());
            List<StrictSemVer> sorted = new ArrayList<>(parsed);
            Collections.shuffle(sorted);
            Collections.sort(sorted);

            for (int i = 0; i < parsed.size() - 1; i++) {
                StrictSemVer a = parsed.get(i);
                StrictSemVer b = parsed.get(i + 1);
                assertTrue(a.compareTo(b) < 0, a + " should be < " + b);
                assertTrue(a.isLessThan(b));
            }
            assertEquals(parsed, sorted);
        }

        @Test
        @DisplayName("Numeric identifiers always have lower precedence than non-numeric identifiers")
        void numericVsAlphaIdentifiers() {
            StrictSemVer num = StrictSemVer.parse("1.0.0-1");
            StrictSemVer alpha = StrictSemVer.parse("1.0.0-a");
            assertTrue(num.compareTo(alpha) < 0);
            assertTrue(alpha.compareTo(num) > 0);
        }

        @Test
        @DisplayName("Build metadata is ignored in precedence comparison")
        void buildMetadataIgnored() {
            StrictSemVer v1 = StrictSemVer.parse("1.0.0-alpha+001");
            StrictSemVer v2 = StrictSemVer.parse("1.0.0-alpha+20130313144700");
            assertEquals(0, v1.compareTo(v2));
            assertTrue(v1.isEquivalentTo(v2));

            StrictSemVer normal1 = StrictSemVer.parse("1.0.0+build.1");
            StrictSemVer normal2 = StrictSemVer.parse("1.0.0+build.2");
            assertEquals(0, normal1.compareTo(normal2));
            assertTrue(normal1.isEquivalentTo(normal2));

            // But equals distinguishes build metadata
            assertNotEquals(v1, v2);
            assertNotEquals(normal1, normal2);
        }

        @Test
        @DisplayName("Large numeric prerelease identifiers do not overflow")
        void largeNumericIdentifiers() {
            StrictSemVer v1 = StrictSemVer.parse("1.0.0-999999999999999999999999");
            StrictSemVer v2 = StrictSemVer.parse("1.0.0-1000000000000000000000000");
            assertTrue(v1.compareTo(v2) < 0);
        }
    }

    @Nested
    @DisplayName("Factory and Equality")
    class FactoryTests {

        @Test
        void factoryMethods() {
            StrictSemVer v1 = StrictSemVer.of(1, 2, 3);
            assertEquals("1.2.3", v1.raw());

            StrictSemVer v2 = StrictSemVer.of(1, 2, 3, "rc.1");
            assertEquals("1.2.3-rc.1", v2.raw());

            StrictSemVer v3 = StrictSemVer.of(1, 2, 3, "rc.1", "sha.abc");
            assertEquals("1.2.3-rc.1+sha.abc", v3.raw());

            assertThrows(IllegalArgumentException.class, () -> StrictSemVer.of(-1, 0, 0));
        }

        @Test
        void equalsAndHashCode() {
            StrictSemVer v1 = StrictSemVer.parse("1.2.3-alpha+build.1");
            StrictSemVer v2 = StrictSemVer.parse("1.2.3-alpha+build.1");
            assertEquals(v1, v2);
            assertEquals(v1.hashCode(), v2.hashCode());
        }
    }
}
