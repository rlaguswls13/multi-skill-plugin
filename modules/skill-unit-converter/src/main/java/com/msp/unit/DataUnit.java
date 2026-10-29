package com.msp.unit;

/**
 * Standard data storage units supporting decimal (SI: 1000) and binary (IEC: 1024) standards.
 */
public enum DataUnit {
    BYTES("B", 1L, false),

    // Decimal SI units (powers of 1000)
    KB("KB", 1_000L, false),
    MB("MB", 1_000_000L, false),
    GB("GB", 1_000_000_000L, false),
    TB("TB", 1_000_000_000_000L, false),
    PB("PB", 1_000_000_000_000_000L, false),

    // Binary IEC units (powers of 1024)
    KiB("KiB", 1_024L, true),
    MiB("MiB", 1_024L * 1_024L, true),
    GiB("GiB", 1_024L * 1_024L * 1_024L, true),
    TiB("TiB", 1_024L * 1_024L * 1_024L * 1_024L, true),
    PiB("PiB", 1_024L * 1_024L * 1_024L * 1_024L * 1_024L, true);

    private final String symbol;
    private final long byteMultiplier;
    private final boolean binary;

    DataUnit(String symbol, long byteMultiplier, boolean binary) {
        this.symbol = symbol;
        this.byteMultiplier = byteMultiplier;
        this.binary = binary;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getByteMultiplier() {
        return byteMultiplier;
    }

    public boolean isBinary() {
        return binary;
    }

    /**
     * Resolves a DataUnit from symbol or name (case-insensitive).
     */
    public static DataUnit fromSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return BYTES;
        }
        for (DataUnit unit : values()) {
            if (unit.symbol.equalsIgnoreCase(symbol) || unit.name().equalsIgnoreCase(symbol)) {
                return unit;
            }
        }
        if (symbol.equalsIgnoreCase("byte") || symbol.equalsIgnoreCase("bytes") || symbol.equalsIgnoreCase("b")) {
            return BYTES;
        }
        throw new IllegalArgumentException("Unknown data unit symbol: " + symbol);
    }
}
