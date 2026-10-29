package com.msp.semver;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SemVer 2.0.0 semantic version implementation with full precedence comparison
 * and version bumping support.
 * Specification: https://semver.org/spec/v2.0.0.html
 */
public final class SemVer implements Comparable<SemVer> {

    private static final Pattern STRICT_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                    "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
                    "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;

    private SemVer(int major, int minor, int patch, String preRelease, String buildMetadata) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version numbers must be non-negative: " + major + "." + minor + "." + patch);
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = (preRelease != null && !preRelease.isBlank()) ? preRelease.trim() : null;
        this.buildMetadata = (buildMetadata != null && !buildMetadata.isBlank()) ? buildMetadata.trim() : null;
    }

    public static SemVer of(int major, int minor, int patch) {
        return new SemVer(major, minor, patch, null, null);
    }

    public static SemVer of(int major, int minor, int patch, String preRelease) {
        return new SemVer(major, minor, patch, preRelease, null);
    }

    public static SemVer of(int major, int minor, int patch, String preRelease, String buildMetadata) {
        return new SemVer(major, minor, patch, preRelease, buildMetadata);
    }

    /**
     * Parses a SemVer 2.0.0 string. Strips optional leading 'v' or 'V' if present.
     */
    public static SemVer parse(String versionString) {
        Objects.requireNonNull(versionString, "versionString must not be null");
        String trimmed = versionString.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }

        Matcher matcher = STRICT_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid SemVer 2.0.0 string: '" + versionString + "'");
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        String preRelease = matcher.group(4);
        String buildMetadata = matcher.group(5);

        return new SemVer(major, minor, patch, preRelease, buildMetadata);
    }

    /**
     * Attempts to parse a SemVer string leniently (e.g. "1.2" -> "1.2.0", "3" -> "3.0.0").
     */
    public static SemVer parseLenient(String versionString) {
        Objects.requireNonNull(versionString, "versionString must not be null");
        String trimmed = versionString.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }

        // Try strict first
        try {
            return parse(trimmed);
        } catch (IllegalArgumentException ignored) {
        }

        // Separate build metadata (+) and pre-release (-)
        String build = null;
        int plusIdx = trimmed.indexOf('+');
        if (plusIdx != -1) {
            build = trimmed.substring(plusIdx + 1);
            trimmed = trimmed.substring(0, plusIdx);
        }

        String pre = null;
        int hyphenIdx = trimmed.indexOf('-');
        if (hyphenIdx != -1) {
            pre = trimmed.substring(hyphenIdx + 1);
            trimmed = trimmed.substring(0, hyphenIdx);
        }

        String[] parts = trimmed.split("\\.");
        int maj = parts.length > 0 && !parts[0].isEmpty() ? Integer.parseInt(parts[0]) : 0;
        int min = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 0;
        int pat = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : 0;

        return new SemVer(maj, min, pat, pre, build);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public Optional<String> getPreRelease() {
        return Optional.ofNullable(preRelease);
    }

    public Optional<String> getBuildMetadata() {
        return Optional.ofNullable(buildMetadata);
    }

    public boolean isPreRelease() {
        return preRelease != null && !preRelease.isEmpty();
    }

    public boolean isStable() {
        return major > 0 && !isPreRelease();
    }

    public boolean isInitialDevelopment() {
        return major == 0;
    }

    /**
     * Bumps major version, resets minor and patch to 0, drops pre-release and build metadata.
     */
    public SemVer bumpMajor() {
        return new SemVer(major + 1, 0, 0, null, null);
    }

    /**
     * Bumps minor version, resets patch to 0, drops pre-release and build metadata.
     */
    public SemVer bumpMinor() {
        return new SemVer(major, minor + 1, 0, null, null);
    }

    /**
     * Bumps patch version, drops pre-release and build metadata.
     */
    public SemVer bumpPatch() {
        return new SemVer(major, minor, patch + 1, null, null);
    }

    public SemVer withPreRelease(String newPreRelease) {
        return new SemVer(major, minor, patch, newPreRelease, buildMetadata);
    }

    public SemVer withBuildMetadata(String newBuildMetadata) {
        return new SemVer(major, minor, patch, preRelease, newBuildMetadata);
    }

    public boolean isGreaterThan(SemVer other) {
        return compareTo(other) > 0;
    }

    public boolean isGreaterThanOrEqualTo(SemVer other) {
        return compareTo(other) >= 0;
    }

    public boolean isLessThan(SemVer other) {
        return compareTo(other) < 0;
    }

    public boolean isLessThanOrEqualTo(SemVer other) {
        return compareTo(other) <= 0;
    }

    /**
     * Returns true if both versions have equivalent precedence (ignores build metadata).
     */
    public boolean isEquivalentTo(SemVer other) {
        return compareTo(other) == 0;
    }

    @Override
    public int compareTo(SemVer other) {
        if (other == null) {
            return 1;
        }

        int c = Integer.compare(this.major, other.major);
        if (c != 0) return c;

        c = Integer.compare(this.minor, other.minor);
        if (c != 0) return c;

        c = Integer.compare(this.patch, other.patch);
        if (c != 0) return c;

        return comparePreRelease(this.preRelease, other.preRelease);
    }

    private static int comparePreRelease(String pr1, String pr2) {
        if (pr1 == null && pr2 == null) return 0;
        if (pr1 == null) return 1;  // normal version > pre-release version
        if (pr2 == null) return -1; // pre-release version < normal version

        String[] parts1 = pr1.split("\\.");
        String[] parts2 = pr2.split("\\.");
        int len = Math.min(parts1.length, parts2.length);

        for (int i = 0; i < len; i++) {
            String p1 = parts1[i];
            String p2 = parts2[i];
            boolean num1 = isNumeric(p1);
            boolean num2 = isNumeric(p2);

            if (num1 && num2) {
                long n1 = Long.parseLong(p1);
                long n2 = Long.parseLong(p2);
                int cmp = Long.compare(n1, n2);
                if (cmp != 0) return cmp;
            } else if (num1) {
                // Numeric identifiers always have lower precedence than non-numeric
                return -1;
            } else if (num2) {
                return 1;
            } else {
                int cmp = p1.compareTo(p2);
                if (cmp != 0) return cmp;
            }
        }
        return Integer.compare(parts1.length, parts2.length);
    }

    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemVer semVer)) return false;
        return major == semVer.major &&
                minor == semVer.minor &&
                patch == semVer.patch &&
                Objects.equals(preRelease, semVer.preRelease) &&
                Objects.equals(buildMetadata, semVer.buildMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease, buildMetadata);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (preRelease != null) {
            sb.append('-').append(preRelease);
        }
        if (buildMetadata != null) {
            sb.append('+').append(buildMetadata);
        }
        return sb.toString();
    }
}
