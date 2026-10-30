package com.msp.color;

/**
 * HSL 색상 모델 (Hue: 0~360, Saturation: 0~100%, Lightness: 0~100%).
 */
public record Hsl(double h, double s, double l) {
    public String toHslString() {
        return String.format("hsl(%.0f, %.0f%%, %.0f%%)", h, s, l);
    }
}
