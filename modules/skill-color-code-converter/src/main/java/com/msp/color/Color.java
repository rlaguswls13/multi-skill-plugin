package com.msp.color;

import java.util.Objects;

/**
 * HEX, RGB, HSL, CMYK 상호 변환 및
 * WCAG 2.1 웹 접근성 명도 대비(Contrast Ratio) 적합성 검증을 지원하는 순수 Java 21 색상 유틸리티.
 */
public record Color(int red, int green, int blue, double alpha) {

    public Color {
        if (red < 0 || red > 255) throw new IllegalArgumentException("Red must be in range [0, 255]: " + red);
        if (green < 0 || green > 255) throw new IllegalArgumentException("Green must be in range [0, 255]: " + green);
        if (blue < 0 || blue > 255) throw new IllegalArgumentException("Blue must be in range [0, 255]: " + blue);
        if (alpha < 0.0 || alpha > 1.0) throw new IllegalArgumentException("Alpha must be in range [0.0, 1.0]: " + alpha);
    }

    public static Color rgb(int red, int green, int blue) {
        return new Color(red, green, blue, 1.0);
    }

    public static Color rgba(int red, int green, int blue, double alpha) {
        return new Color(red, green, blue, alpha);
    }

    public static Color fromHex(String hex) {
        Objects.requireNonNull(hex, "hex must not be null");
        String clean = hex.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }

        if (clean.length() == 3) { // #RGB
            int r = Integer.parseInt(clean.substring(0, 1).repeat(2), 16);
            int g = Integer.parseInt(clean.substring(1, 2).repeat(2), 16);
            int b = Integer.parseInt(clean.substring(2, 3).repeat(2), 16);
            return rgb(r, g, b);
        } else if (clean.length() == 4) { // #RGBA
            int r = Integer.parseInt(clean.substring(0, 1).repeat(2), 16);
            int g = Integer.parseInt(clean.substring(1, 2).repeat(2), 16);
            int b = Integer.parseInt(clean.substring(2, 3).repeat(2), 16);
            int a = Integer.parseInt(clean.substring(3, 4).repeat(2), 16);
            return rgba(r, g, b, a / 255.0);
        } else if (clean.length() == 6) { // #RRGGBB
            int r = Integer.parseInt(clean.substring(0, 2), 16);
            int g = Integer.parseInt(clean.substring(2, 4), 16);
            int b = Integer.parseInt(clean.substring(4, 6), 16);
            return rgb(r, g, b);
        } else if (clean.length() == 8) { // #RRGGBBAA
            int r = Integer.parseInt(clean.substring(0, 2), 16);
            int g = Integer.parseInt(clean.substring(2, 4), 16);
            int b = Integer.parseInt(clean.substring(4, 6), 16);
            int a = Integer.parseInt(clean.substring(6, 8), 16);
            return rgba(r, g, b, a / 255.0);
        } else {
            throw new IllegalArgumentException("Invalid hex color format: " + hex);
        }
    }

    public String toHex() {
        if (alpha >= 1.0) {
            return String.format("#%02X%02X%02X", red, green, blue);
        }
        int a = (int) Math.round(alpha * 255);
        return String.format("#%02X%02X%02X%02X", red, green, blue, a);
    }

    public String toRgbString() {
        return String.format("rgb(%d, %d, %d)", red, green, blue);
    }

    public String toRgbaString() {
        return String.format("rgba(%d, %d, %d, %.2f)", red, green, blue, alpha);
    }

    public Hsl toHsl() {
        double r = red / 255.0;
        double g = green / 255.0;
        double b = blue / 255.0;

        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;

        double l = (max + min) / 2.0;
        double s = 0.0;
        double h = 0.0;

        if (delta != 0) {
            s = (l <= 0.5) ? (delta / (max + min)) : (delta / (2.0 - max - min));
            if (max == r) {
                h = ((g - b) / delta) + (g < b ? 6.0 : 0.0);
            } else if (max == g) {
                h = ((b - r) / delta) + 2.0;
            } else {
                h = ((r - g) / delta) + 4.0;
            }
            h *= 60.0;
        }

        return new Hsl(h, s * 100.0, l * 100.0);
    }

    public Cmyk toCmyk() {
        if (red == 0 && green == 0 && blue == 0) {
            return new Cmyk(0, 0, 0, 100);
        }
        double r = red / 255.0;
        double g = green / 255.0;
        double b = blue / 255.0;

        double k = 1.0 - Math.max(r, Math.max(g, b));
        double c = (1.0 - r - k) / (1.0 - k);
        double m = (1.0 - g - k) / (1.0 - k);
        double y = (1.0 - b - k) / (1.0 - k);

        return new Cmyk(c * 100.0, m * 100.0, y * 100.0, k * 100.0);
    }

    /**
     * W3C WCAG 2.1 상대 명도(Relative Luminance) 계산 (0.0 ~ 1.0).
     */
    public double luminance() {
        double r = toLinear(red / 255.0);
        double g = toLinear(green / 255.0);
        double b = toLinear(blue / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double toLinear(double c) {
        return (c <= 0.04045) ? (c / 12.92) : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /**
     * 배경색과의 WCAG 명도 대비율(Contrast Ratio, 1.0:1 ~ 21.0:1) 계산.
     */
    public double contrastRatio(Color background) {
        Objects.requireNonNull(background, "background color must not be null");
        double l1 = this.luminance();
        double l2 = background.luminance();
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    /**
     * WCAG 2.1 AA 기준 적합성 검증 (일반 텍스트 대비비 >= 4.5:1).
     */
    public boolean isWcagAa(Color background) {
        return contrastRatio(background) >= 4.5;
    }

    /**
     * WCAG 2.1 AAA 기준 적합성 검증 (일반 텍스트 대비비 >= 7.0:1).
     */
    public boolean isWcagAaa(Color background) {
        return contrastRatio(background) >= 7.0;
    }
}
