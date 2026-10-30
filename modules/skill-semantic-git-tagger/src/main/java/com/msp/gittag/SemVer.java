package com.msp.gittag;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

    private static final Pattern SEMVER_PATTERN = Pattern.compile("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)");

    public static SemVer of(int major, int minor, int patch) {
        return new SemVer(major, minor, patch);
    }

    public static SemVer parse(String versionStr) {
        Objects.requireNonNull(versionStr, "versionStr must not be null");
        Matcher m = SEMVER_PATTERN.matcher(versionStr.trim());
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid SemVer string: " + versionStr);
        }
        return new SemVer(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3))
        );
    }

    public SemVer bumpMajor() {
        return new SemVer(major + 1, 0, 0);
    }

    public SemVer bumpMinor() {
        return new SemVer(major, minor + 1, 0);
    }

    public SemVer bumpPatch() {
        return new SemVer(major, minor, patch + 1);
    }

    public String format(boolean withPrefix) {
        return (withPrefix ? "v" : "") + major + "." + minor + "." + patch;
    }

    @Override
    public int compareTo(SemVer o) {
        int c1 = Integer.compare(this.major, o.major);
        if (c1 != 0) return c1;
        int c2 = Integer.compare(this.minor, o.minor);
        if (c2 != 0) return c2;
        return Integer.compare(this.patch, o.patch);
    }

    @Override
    public String toString() {
        return format(true);
    }
}
