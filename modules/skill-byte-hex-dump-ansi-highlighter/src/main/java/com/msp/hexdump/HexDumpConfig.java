package com.msp.hexdump;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration options for {@link HexDumper}.
 */
public record HexDumpConfig(
    int bytesPerLine,
    boolean showOffset,
    boolean showAscii,
    boolean uppercaseHex,
    boolean ansiColors,
    AnsiColor nullColor,
    AnsiColor asciiColor,
    AnsiColor controlColor,
    AnsiColor highByteColor,
    List<ByteRangeHighlight> rangeHighlights
) {
    public HexDumpConfig {
        if (bytesPerLine < 1) {
            throw new IllegalArgumentException("bytesPerLine must be at least 1");
        }
        Objects.requireNonNull(nullColor, "nullColor cannot be null");
        Objects.requireNonNull(asciiColor, "asciiColor cannot be null");
        Objects.requireNonNull(controlColor, "controlColor cannot be null");
        Objects.requireNonNull(highByteColor, "highByteColor cannot be null");
        rangeHighlights = List.copyOf(Objects.requireNonNull(rangeHighlights, "rangeHighlights cannot be null"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static HexDumpConfig defaultConfig() {
        return builder().build();
    }

    public static final class Builder {
        private int bytesPerLine = 16;
        private boolean showOffset = true;
        private boolean showAscii = true;
        private boolean uppercaseHex = false;
        private boolean ansiColors = true;
        private AnsiColor nullColor = AnsiColor.GRAY;
        private AnsiColor asciiColor = AnsiColor.GREEN;
        private AnsiColor controlColor = AnsiColor.YELLOW;
        private AnsiColor highByteColor = AnsiColor.MAGENTA;
        private final List<ByteRangeHighlight> rangeHighlights = new ArrayList<>();

        public Builder bytesPerLine(int bytesPerLine) {
            this.bytesPerLine = bytesPerLine;
            return this;
        }

        public Builder showOffset(boolean showOffset) {
            this.showOffset = showOffset;
            return this;
        }

        public Builder showAscii(boolean showAscii) {
            this.showAscii = showAscii;
            return this;
        }

        public Builder uppercaseHex(boolean uppercaseHex) {
            this.uppercaseHex = uppercaseHex;
            return this;
        }

        public Builder ansiColors(boolean ansiColors) {
            this.ansiColors = ansiColors;
            return this;
        }

        public Builder nullColor(AnsiColor color) {
            this.nullColor = Objects.requireNonNull(color);
            return this;
        }

        public Builder asciiColor(AnsiColor color) {
            this.asciiColor = Objects.requireNonNull(color);
            return this;
        }

        public Builder controlColor(AnsiColor color) {
            this.controlColor = Objects.requireNonNull(color);
            return this;
        }

        public Builder highByteColor(AnsiColor color) {
            this.highByteColor = Objects.requireNonNull(color);
            return this;
        }

        public Builder addRangeHighlight(ByteRangeHighlight highlight) {
            this.rangeHighlights.add(Objects.requireNonNull(highlight));
            return this;
        }

        public HexDumpConfig build() {
            return new HexDumpConfig(
                    bytesPerLine,
                    showOffset,
                    showAscii,
                    uppercaseHex,
                    ansiColors,
                    nullColor,
                    asciiColor,
                    controlColor,
                    highByteColor,
                    rangeHighlights
            );
        }
    }
}
