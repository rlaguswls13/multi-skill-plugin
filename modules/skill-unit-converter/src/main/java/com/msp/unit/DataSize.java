package com.msp.unit;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable representation of a data storage size.
 * <p>
 * Supports cross-conversion between binary (1024) and decimal (1000) prefixes,
 * robust text parsing ("10.5 MB", "512 KiB"), arithmetic, and human-readable formatting.
 */
public final class DataSize implements Comparable<DataSize> {

    public static final DataSize ZERO = new DataSize(0L);

    private static final Pattern PARSE_PATTERN =
            Pattern.compile("^\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z]+)?\\s*$");

    private final long bytes;

    private DataSize(long bytes) {
        this.bytes = bytes;
    }

    public static DataSize ofBytes(long bytes) {
        return new DataSize(bytes);
    }

    public static DataSize of(double amount, DataUnit unit) {
        Objects.requireNonNull(unit, "unit cannot be null");
        return new DataSize(Math.round(amount * unit.getByteMultiplier()));
    }

    public static DataSize ofKilobytes(double kb) {
        return of(kb, DataUnit.KB);
    }

    public static DataSize ofMegabytes(double mb) {
        return of(mb, DataUnit.MB);
    }

    public static DataSize ofGigabytes(double gb) {
        return of(gb, DataUnit.GB);
    }

    public static DataSize ofTerabytes(double tb) {
        return of(tb, DataUnit.TB);
    }

    public static DataSize ofPetabytes(double pb) {
        return of(pb, DataUnit.PB);
    }

    public static DataSize ofKibibytes(double kib) {
        return of(kib, DataUnit.KiB);
    }

    public static DataSize ofMebibytes(double mib) {
        return of(mib, DataUnit.MiB);
    }

    public static DataSize ofGibibytes(double gib) {
        return of(gib, DataUnit.GiB);
    }

    public static DataSize ofTebibytes(double tib) {
        return of(tib, DataUnit.TiB);
    }

    public static DataSize ofPebibytes(double pib) {
        return of(pib, DataUnit.PiB);
    }

    /**
     * Parses human data size strings such as "10.5 MB", "512KiB", "1024 B".
     */
    public static DataSize parse(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        Matcher matcher = PARSE_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Cannot parse data size text: \"" + text + "\"");
        }

        double val = Double.parseDouble(matcher.group(1));
        String unitStr = matcher.group(2);
        DataUnit unit = (unitStr == null || unitStr.isBlank()) ? DataUnit.BYTES : DataUnit.fromSymbol(unitStr);

        return of(val, unit);
    }

    /**
     * Parses data size string using binary 1024 base prefixes even for KB/MB/GB tokens.
     */
    public static DataSize parseBinary(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        Matcher matcher = PARSE_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Cannot parse data size text: \"" + text + "\"");
        }

        double val = Double.parseDouble(matcher.group(1));
        String unitStr = matcher.group(2);
        if (unitStr == null || unitStr.isBlank()) {
            return ofBytes(Math.round(val));
        }

        DataUnit unit = switch (unitStr.toUpperCase(Locale.US)) {
            case "KB", "KIB" -> DataUnit.KiB;
            case "MB", "MIB" -> DataUnit.MiB;
            case "GB", "GIB" -> DataUnit.GiB;
            case "TB", "TIB" -> DataUnit.TiB;
            case "PB", "PIB" -> DataUnit.PiB;
            default -> DataUnit.fromSymbol(unitStr);
        };
        return of(val, unit);
    }

    public long toBytes() {
        return bytes;
    }

    public double to(DataUnit unit) {
        Objects.requireNonNull(unit, "unit cannot be null");
        return (double) bytes / unit.getByteMultiplier();
    }

    public double toKilobytes() {
        return to(DataUnit.KB);
    }

    public double toMegabytes() {
        return to(DataUnit.MB);
    }

    public double toGigabytes() {
        return to(DataUnit.GB);
    }

    public double toTerabytes() {
        return to(DataUnit.TB);
    }

    public double toPetabytes() {
        return to(DataUnit.PB);
    }

    public double toKibibytes() {
        return to(DataUnit.KiB);
    }

    public double toMebibytes() {
        return to(DataUnit.MiB);
    }

    public double toGibibytes() {
        return to(DataUnit.GiB);
    }

    public double toTebibytes() {
        return to(DataUnit.TiB);
    }

    public double toPebibytes() {
        return to(DataUnit.PiB);
    }

    /**
     * Formats this DataSize to human-readable string using binary IEC prefixes (1024 base: B, KiB, MiB, GiB, ...).
     */
    public String toHumanReadable() {
        return toHumanReadable(true);
    }

    /**
     * Formats this DataSize to human-readable string using binary (1024) or decimal (1000) prefixes.
     *
     * @param binary true for KiB/MiB/GiB (1024), false for KB/MB/GB (1000)
     */
    public String toHumanReadable(boolean binary) {
        long absBytes = Math.abs(bytes);
        long unitSize = binary ? 1024L : 1000L;
        String[] units = binary
                ? new String[]{"B", "KiB", "MiB", "GiB", "TiB", "PiB"}
                : new String[]{"B", "KB", "MB", "GB", "TB", "PB"};

        if (absBytes < unitSize) {
            return bytes + " " + units[0];
        }

        int exp = (int) (Math.log(absBytes) / Math.log(unitSize));
        exp = Math.min(exp, units.length - 1);
        double value = (double) bytes / Math.pow(unitSize, exp);

        if (Math.abs(value - Math.round(value)) < 1e-4) {
            return String.format(Locale.US, "%d %s", Math.round(value), units[exp]);
        } else {
            return String.format(Locale.US, "%.2f %s", value, units[exp]);
        }
    }

    public DataSize plus(DataSize other) {
        Objects.requireNonNull(other, "other cannot be null");
        return new DataSize(Math.addExact(this.bytes, other.bytes));
    }

    public DataSize minus(DataSize other) {
        Objects.requireNonNull(other, "other cannot be null");
        return new DataSize(Math.subtractExact(this.bytes, other.bytes));
    }

    @Override
    public int compareTo(DataSize o) {
        return Long.compare(this.bytes, o.bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataSize dataSize)) return false;
        return bytes == dataSize.bytes;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bytes);
    }

    @Override
    public String toString() {
        return toHumanReadable();
    }
}
