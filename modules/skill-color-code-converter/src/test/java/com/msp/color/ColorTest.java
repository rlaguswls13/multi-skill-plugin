package com.msp.color;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorTest {

    @Test
    @DisplayName("Hex parsing and conversion test")
    void testHexConversions() {
        Color c1 = Color.fromHex("#336699");
        assertEquals(0x33, c1.red());
        assertEquals(0x66, c1.green());
        assertEquals(0x99, c1.blue());
        assertEquals("#336699", c1.toHex());
        assertEquals("rgb(51, 102, 153)", c1.toRgbString());

        // Short hex #RGB
        Color c2 = Color.fromHex("#F00");
        assertEquals(255, c2.red());
        assertEquals(0, c2.green());
        assertEquals(0, c2.blue());
        assertEquals("#FF0000", c2.toHex());
    }

    @Test
    @DisplayName("HSL and CMYK conversions test")
    void testHslCmyk() {
        Color red = Color.rgb(255, 0, 0);
        Hsl hsl = red.toHsl();
        assertEquals(0.0, hsl.h(), 0.01);
        assertEquals(100.0, hsl.s(), 0.01);
        assertEquals(50.0, hsl.l(), 0.01);

        Cmyk cmyk = red.toCmyk();
        assertEquals(0.0, cmyk.c(), 0.01);
        assertEquals(100.0, cmyk.m(), 0.01);
        assertEquals(100.0, cmyk.y(), 0.01);
        assertEquals(0.0, cmyk.k(), 0.01);
    }

    @Test
    @DisplayName("WCAG 2.1 Contrast ratio and accessibility test")
    void testContrastRatio() {
        Color black = Color.rgb(0, 0, 0);
        Color white = Color.rgb(255, 255, 255);

        double ratio = black.contrastRatio(white);
        assertEquals(21.0, ratio, 0.1);
        assertTrue(black.isWcagAa(white));
        assertTrue(black.isWcagAaa(white));

        Color lightGray = Color.rgb(200, 200, 200);
        assertFalse(lightGray.isWcagAa(white));
    }
}
