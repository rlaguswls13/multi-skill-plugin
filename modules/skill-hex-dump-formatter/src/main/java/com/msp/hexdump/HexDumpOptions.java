package com.msp.hexdump;

public record HexDumpOptions(
        int bytesPerLine,
        boolean showAscii,
        boolean uppercase,
        boolean groupHalf
) {
    public static HexDumpOptions standard() {
        return new HexDumpOptions(16, true, true, true);
    }
}
