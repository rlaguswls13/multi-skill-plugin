package com.msp.hexdump;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Wireshark and Hexdump style 3-column binary formatter with semantic ANSI syntax highlighting.
 */
public final class HexDumper {

    private final HexDumpConfig config;

    public HexDumper() {
        this(HexDumpConfig.defaultConfig());
    }

    public HexDumper(HexDumpConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public static HexDumper create() {
        return new HexDumper();
    }

    public static String dump(byte[] data) {
        return new HexDumper().format(data);
    }

    public static String dump(byte[] data, HexDumpConfig config) {
        return new HexDumper(config).format(data);
    }

    public HexDumper withAnsiColors(boolean enabled) {
        return new HexDumper(HexDumpConfig.builder()
                .bytesPerLine(config.bytesPerLine())
                .showOffset(config.showOffset())
                .showAscii(config.showAscii())
                .uppercaseHex(config.uppercaseHex())
                .ansiColors(enabled)
                .nullColor(config.nullColor())
                .asciiColor(config.asciiColor())
                .controlColor(config.controlColor())
                .highByteColor(config.highByteColor())
                .build());
    }

    public HexDumper withBytesPerLine(int count) {
        return new HexDumper(HexDumpConfig.builder()
                .bytesPerLine(count)
                .showOffset(config.showOffset())
                .showAscii(config.showAscii())
                .uppercaseHex(config.uppercaseHex())
                .ansiColors(config.ansiColors())
                .nullColor(config.nullColor())
                .asciiColor(config.asciiColor())
                .controlColor(config.controlColor())
                .highByteColor(config.highByteColor())
                .build());
    }

    public HexDumper withShowOffset(boolean show) {
        return new HexDumper(HexDumpConfig.builder()
                .bytesPerLine(config.bytesPerLine())
                .showOffset(show)
                .showAscii(config.showAscii())
                .uppercaseHex(config.uppercaseHex())
                .ansiColors(config.ansiColors())
                .nullColor(config.nullColor())
                .asciiColor(config.asciiColor())
                .controlColor(config.controlColor())
                .highByteColor(config.highByteColor())
                .build());
    }

    public HexDumper withShowAscii(boolean show) {
        return new HexDumper(HexDumpConfig.builder()
                .bytesPerLine(config.bytesPerLine())
                .showOffset(config.showOffset())
                .showAscii(show)
                .uppercaseHex(config.uppercaseHex())
                .ansiColors(config.ansiColors())
                .nullColor(config.nullColor())
                .asciiColor(config.asciiColor())
                .controlColor(config.controlColor())
                .highByteColor(config.highByteColor())
                .build());
    }

    public HexDumper withUppercaseHex(boolean upper) {
        return new HexDumper(HexDumpConfig.builder()
                .bytesPerLine(config.bytesPerLine())
                .showOffset(config.showOffset())
                .showAscii(config.showAscii())
                .uppercaseHex(upper)
                .ansiColors(config.ansiColors())
                .nullColor(config.nullColor())
                .asciiColor(config.asciiColor())
                .controlColor(config.controlColor())
                .highByteColor(config.highByteColor())
                .build());
    }

    public HexDumper highlightRange(int offset, int length, AnsiColor color) {
        return highlightRange(offset, length, color, null);
    }

    public HexDumper highlightRange(int offset, int length, AnsiColor color, String label) {
        HexDumpConfig.Builder b = HexDumpConfig.builder()
                .bytesPerLine(config.bytesPerLine())
                .showOffset(config.showOffset())
                .showAscii(config.showAscii())
                .uppercaseHex(config.uppercaseHex())
                .ansiColors(config.ansiColors())
                .nullColor(config.nullColor())
                .asciiColor(config.asciiColor())
                .controlColor(config.controlColor())
                .highByteColor(config.highByteColor());
        config.rangeHighlights().forEach(b::addRangeHighlight);
        b.addRangeHighlight(new ByteRangeHighlight(offset, length, color, label));
        return new HexDumper(b.build());
    }

    public String format(byte[] data) {
        if (data == null) {
            return "";
        }
        return format(data, 0, data.length);
    }

    public String format(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        try {
            format(data, offset, length, sb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }

    public void format(byte[] data, int offset, int length, Appendable out) throws IOException {
        if (data == null || length <= 0) {
            return;
        }
        if (offset < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException("Offset " + offset + " and length " + length + " out of bounds for byte[" + data.length + "]");
        }

        int bytesPerLine = config.bytesPerLine();
        int halfBlock = bytesPerLine / 2;
        int lines = (length + bytesPerLine - 1) / bytesPerLine;

        for (int line = 0; line < lines; line++) {
            int lineByteOffset = line * bytesPerLine;
            int bytesInThisLine = Math.min(bytesPerLine, length - lineByteOffset);

            // 1. Offset Column
            if (config.showOffset()) {
                String offsetFormat = config.uppercaseHex() ? "%08X  " : "%08x  ";
                out.append(String.format(offsetFormat, lineByteOffset));
            }

            // 2. Hex Bytes Column
            for (int i = 0; i < bytesPerLine; i++) {
                if (i > 0) {
                    if (halfBlock > 0 && i == halfBlock) {
                        out.append("  "); // extra block separator space
                    } else {
                        out.append(' ');
                    }
                }

                if (i < bytesInThisLine) {
                    int globalIndex = lineByteOffset + i;
                    int b = data[offset + globalIndex] & 0xFF;
                    String hexStr = formatByte(b);

                    if (config.ansiColors()) {
                        AnsiColor color = resolveColor(globalIndex, b);
                        out.append(color.apply(hexStr));
                    } else {
                        out.append(hexStr);
                    }
                } else {
                    // Padding spaces for incomplete line
                    out.append("  ");
                }
            }

            // 3. ASCII Column
            if (config.showAscii()) {
                out.append("  |");
                for (int i = 0; i < bytesInThisLine; i++) {
                    int globalIndex = lineByteOffset + i;
                    int b = data[offset + globalIndex] & 0xFF;
                    char asciiChar = (b >= 0x20 && b <= 0x7E) ? (char) b : '.';
                    String charStr = String.valueOf(asciiChar);

                    if (config.ansiColors()) {
                        AnsiColor color = resolveColor(globalIndex, b);
                        out.append(color.apply(charStr));
                    } else {
                        out.append(charStr);
                    }
                }
                out.append('|');
            }

            if (line < lines - 1) {
                out.append('\n');
            }
        }
    }

    private String formatByte(int unsignedByte) {
        int hi = (unsignedByte >> 4) & 0x0F;
        int lo = unsignedByte & 0x0F;
        char[] digits = config.uppercaseHex() ? HEX_DIGITS_UPPER : HEX_DIGITS_LOWER;
        return "" + digits[hi] + digits[lo];
    }

    private AnsiColor resolveColor(int globalIndex, int unsignedByte) {
        // Range highlights take highest precedence
        for (ByteRangeHighlight rh : config.rangeHighlights()) {
            if (rh.contains(globalIndex)) {
                return rh.color();
            }
        }

        // Semantic byte category color
        ByteCategory cat = ByteCategory.of(unsignedByte);
        return switch (cat) {
            case NULL_BYTE -> config.nullColor();
            case PRINTABLE_ASCII -> config.asciiColor();
            case CONTROL_CHAR -> config.controlColor();
            case HIGH_BINARY -> config.highByteColor();
        };
    }

    private static final char[] HEX_DIGITS_LOWER = "0123456789abcdef".toCharArray();
    private static final char[] HEX_DIGITS_UPPER = "0123456789ABCDEF".toCharArray();
}
