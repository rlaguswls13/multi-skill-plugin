package com.msp.semvercomp;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict Semantic Versioning 2.0.0 implementation adhering strictly to the SemVer 2.0.0 specification.
 * <p>
 * Supports Major.Minor.Patch, Pre-release tags (e.g. {@code alpha}, {@code beta.1}, {@code rc.2}),
 * and Build metadata (e.g. {@code +20261031}, {@code +exp.sha.5114f85}).
 * <p>
 * Key SemVer 2.0 Precedence Rules:
 * <ul>
 *   <li>Pre-release version has lower precedence than normal version (e.g. {@code 1.0.0-alpha < 1.0.0}).</li>
 *   <li>Pre-release identifiers are compared dot-by-dot: numeric vs numeric (numerical),
 *       string vs string (ASCII lexical), numeric vs string (numeric ALWAYS lower precedence).</li>
 *   <li>Build metadata is completely ignored in precedence comparison (e.g. {@code 1.0.0+build1 == 1.0.0+build2}).</li>
 * </ul>
 */
public final class StrictSemVer implements Comparable<StrictSemVer>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Official SemVer 2.0.0 regular expression with capturing groups:
     * Group 1: Major
     * Group 2: Minor
     * Group 3: Patch
     * Group 4: Prerelease
     * Group 5: Build metadata
     */
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    private static final Pattern NUMERIC_IDENTIFIER = Pattern.compile("^\\d+$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String prerelease;
    private final String buildMetadata;
    private final String raw;

    private StrictSemVer(int major, int minor, int patch, String prerelease, String buildMetadata) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Major, minor, and patch numbers must be non-negative integers");
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
        this.buildMetadata = buildMetadata;

        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (prerelease != null && !prerelease.isEmpty()) {
            sb.append('-').append(prerelease);
        }
        if (buildMetadata != null && !buildMetadata.isEmpty()) {
            sb.append('+').append(buildMetadata);
        }
        this.raw = sb.toString();
    }

    /**
     * Parses a strict SemVer 2.0.0 version string.
     *
     * @param versionString string representation of version
     * @return parsed {@link StrictSemVer} instance
     * @throws IllegalArgumentException if the version string violates SemVer 2.0.0 specifications
     */
    public static StrictSemVer parse(String versionString) {
        Objects.requireNonNull(versionString, "versionString must not be null");
        String trimmed = versionString.trim();
        Matcher matcher = SEMVER_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid SemVer 2.0.0 version string: '" + versionString + "'");
        }

        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));
            String prerelease = matcher.group(4);
            String build = matcher.group(5);
            return new StrictSemVer(major, minor, patch, prerelease, build);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Version numbers exceed standard integer capacity: " + versionString, e);
        }
    }

    /**
     * Tries to parse a SemVer string without throwing an exception.
     *
     * @param versionString version string to parse
     * @return Optional containing StrictSemVer if valid, empty otherwise
     */
    public static Optional<StrictSemVer> tryParse(String versionString) {
        if (versionString == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse(versionString));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Factory for normal release version.
     */
    public static StrictSemVer of(int major, int minor, int patch) {
        return new StrictSemVer(major, minor, patch, null, null);
    }

    /**
     * Factory for pre-release version without build metadata.
     */
    public static StrictSemVer of(int major, int minor, int patch, String prerelease) {
        StrictSemVer v = new StrictSemVer(major, minor, patch, prerelease, null);
        // Validate constructed form matches grammar
        if (!SEMVER_PATTERN.matcher(v.raw).matches()) {
            throw new IllegalArgumentException("Invalid pre-release identifier: " + prerelease);
        }
        return v;
    }

    /**
     * Factory for full version with pre-release and build metadata.
     */
    public static StrictSemVer of(int major, int minor, int patch, String prerelease, String buildMetadata) {
        StrictSemVer v = new StrictSemVer(major, minor, patch, prerelease, buildMetadata);
        if (!SEMVER_PATTERN.matcher(v.raw).matches()) {
            throw new IllegalArgumentException("Invalid semver parameters: prerelease=" + prerelease + ", build=" + buildMetadata);
        }
        return v;
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public Optional<String> prerelease() {
        return Optional.ofNullable(prerelease);
    }

    public Optional<String> buildMetadata() {
        return Optional.ofNullable(buildMetadata);
    }

    public boolean isPrerelease() {
        return prerelease != null && !prerelease.isEmpty();
    }

    public boolean hasBuildMetadata() {
        return buildMetadata != null && !buildMetadata.isEmpty();
    }

    public String normalVersion() {
        return major + "." + minor + "." + patch;
    }

    public String raw() {
        return raw;
    }

    public boolean isGreaterThan(StrictSemVer other) {
        return compareTo(other) > 0;
    }

    public boolean isGreaterThanOrEqualTo(StrictSemVer other) {
        return compareTo(other) >= 0;
    }

    public boolean isLessThan(StrictSemVer other) {
        return compareTo(other) < 0;
    }

    public boolean isLessThanOrEqualTo(StrictSemVer other) {
        return compareTo(other) <= 0;
    }

    /**
     * Checks if two versions have the same precedence per SemVer 2.0.0 (ignoring build metadata).
     */
    public boolean isEquivalentTo(StrictSemVer other) {
        return compareTo(other) == 0;
    }

    @Override
    public int compareTo(StrictSemVer other) {
        Objects.requireNonNull(other, "Cannot compare to null StrictSemVer");

        int c = Integer.compare(this.major, other.major);
        if (c != 0) return c;

        c = Integer.compare(this.minor, other.minor);
        if (c != 0) return c;

        c = Integer.compare(this.patch, other.patch);
        if (c != 0) return c;

        return comparePrerelease(this.prerelease, other.prerelease);
    }

    private static int comparePrerelease(String preA, String preB) {
        // Spec 11.3: When major, minor, and patch are equal, a pre-release version has lower
        // precedence than a normal version. Example: 1.0.0-alpha < 1.0.0.
        if (preA == null && preB == null) {
            return 0;
        }
        if (preA == null) {
            return 1; // Normal version has higher precedence than pre-release
        }
        if (preB == null) {
            return -1; // Pre-release has lower precedence than normal version
        }

        // Both have pre-release identifiers; compare dot-separated identifiers left-to-right
        String[] partsA = preA.split("\\.");
        String[] partsB = preB.split("\\.");

        int minLen = Math.min(partsA.length, partsB.length);
        for (int i = 0; i < minLen; i++) {
            int comp = compareIdentifier(partsA[i], partsB[i]);
            if (comp != 0) {
                return comp;
            }
        }

        // Spec 11.4.4: A larger set of pre-release fields has a higher precedence than a smaller set,
        // if all of the preceding identifiers are equal.
        return Integer.compare(partsA.length, partsB.length);
    }

    private static int compareIdentifier(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }

        boolean aNumeric = NUMERIC_IDENTIFIER.matcher(a).matches();
        boolean bNumeric = NUMERIC_IDENTIFIER.matcher(b).matches();

        if (aNumeric && bNumeric) {
            // Compare numerically
            try {
                long numA = Long.parseLong(a);
                long numB = Long.parseLong(b);
                return Long.compare(numA, numB);
            } catch (NumberFormatException e) {
                // If it overflows Long, fallback to BigInteger
                return new BigInteger(a).compareTo(new BigInteger(b));
            }
        }

        if (aNumeric && !bNumeric) {
            // Spec 11.4.3: Numeric identifiers always have lower precedence than non-numeric identifiers.
            return -1;
        }

        if (!aNumeric && bNumeric) {
            return 1;
        }

        // Spec 11.4.2: Identifiers with letters or hyphens are compared lexically in ASCII sort order.
        return a.compareTo(b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StrictSemVer that = (StrictSemVer) o;
        return major == that.major &&
                minor == that.minor &&
                patch == that.patch &&
                Objects.equals(prerelease, that.prerelease) &&
                Objects.equals(buildMetadata, that.buildMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, prerelease, buildMetadata);
    }

    @Override
    public String toString() {
        return raw;
    }
}
