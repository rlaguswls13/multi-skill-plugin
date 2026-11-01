package com.msp.byteunit;

/**
 * Standard unit measurement system for byte sizes.
 */
public enum ByteUnitStandard {
    /**
     * IEC standard (binary, base 1024): B, KiB, MiB, GiB, TiB, PiB, EiB.
     */
    IEC(1024L, new String[]{"B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"}),

    /**
     * SI standard (metric, base 1000): B, KB, MB, GB, TB, PB, EB.
     */
    SI(1000L, new String[]{"B", "KB", "MB", "GB", "TB", "PB", "EB"});

    private final long base;
    private final String[] units;

    ByteUnitStandard(long base, String[] units) {
        this.base = base;
        this.units = units;
    }

    public long base() {
        return base;
    }

    public String[] units() {
        return units.clone();
    }
}
