package com.msp.color;

/**
 * CMYK 색상 모델 (Cyan: 0~100%, Magenta: 0~100%, Yellow: 0~100%, Black(Key): 0~100%).
 */
public record Cmyk(double c, double m, double y, double k) {
    public String toCmykString() {
        return String.format("cmyk(%.0f%%, %.0f%%, %.0f%%, %.0f%%)", c, m, y, k);
    }
}
