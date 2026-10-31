package com.msp.base32;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Base32CodecTest {

    @Test
    @DisplayName("RFC 4648 Standard Vectors Encoding & Decoding")
    void testRfc4648Vectors() {
        assertEquals("", Base32Codec.encode("".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MY======", Base32Codec.encode("f".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXQ====", Base32Codec.encode("fo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXW6===", Base32Codec.encode("foo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXW6YQ=", Base32Codec.encode("foob".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXW6YTB", Base32Codec.encode("fooba".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXW6YTBOI======", Base32Codec.encode("foobar".getBytes(StandardCharsets.UTF_8)));

        assertEquals("", new String(Base32Codec.decode("")));
        assertEquals("f", new String(Base32Codec.decode("MY======")));
        assertEquals("fo", new String(Base32Codec.decode("MZXQ====")));
        assertEquals("foo", new String(Base32Codec.decode("MZXW6===")));
        assertEquals("foob", new String(Base32Codec.decode("MZXW6YQ=")));
        assertEquals("fooba", new String(Base32Codec.decode("MZXW6YTB")));
        assertEquals("foobar", new String(Base32Codec.decode("MZXW6YTBOI======")));
    }

    @Test
    @DisplayName("RFC 4648 Unpadded Decoding and Case Insensitivity")
    void testRfc4648UnpaddedAndLowercase() {
        assertEquals("foobar", new String(Base32Codec.decode("mzxw6ytboi")));
        assertEquals("foobar", new String(Base32Codec.decode("MZXW6YTBOI")));
        assertEquals("foob", new String(Base32Codec.decode("MZXW6YQ")));
    }

    @Test
    @DisplayName("RFC 4648 Extended Hex Vectors")
    void testRfc4648HexVectors() {
        Base32Variant hex = Base32Variant.RFC4648_HEX;
        assertEquals("", Base32Codec.encode("".getBytes(StandardCharsets.UTF_8), hex, true));
        assertEquals("CO======", Base32Codec.encode("f".getBytes(StandardCharsets.UTF_8), hex, true));
        assertEquals("CPNG====", Base32Codec.encode("fo".getBytes(StandardCharsets.UTF_8), hex, true));
        assertEquals("CPNMU===", Base32Codec.encode("foo".getBytes(StandardCharsets.UTF_8), hex, true));
        assertEquals("CPNMUOG=", Base32Codec.encode("foob".getBytes(StandardCharsets.UTF_8), hex, true));
        assertEquals("CPNMUOJ1", Base32Codec.encode("fooba".getBytes(StandardCharsets.UTF_8), hex, true));
        assertEquals("CPNMUOJ1E8======", Base32Codec.encode("foobar".getBytes(StandardCharsets.UTF_8), hex, true));

        assertEquals("foobar", new String(Base32Codec.decode("cpnmuoj1e8======", hex)));
    }

    @Test
    @DisplayName("Crockford Base32 Human Error Tolerance")
    void testCrockfordHumanErrorCorrection() {
        Base32Variant crockford = Base32Variant.CROCKFORD;
        byte[] data = "Hello!".getBytes(StandardCharsets.UTF_8);

        String encoded = Base32Codec.encode(data, crockford);
        assertFalse(encoded.contains("="));

        // Normal decoding
        assertArrayEquals(data, Base32Codec.decode(encoded, crockford));

        // Crockford hyphens ignoring: e.g. 91JPR-V3F41
        String withHyphens = encoded.substring(0, 3) + "-" + encoded.substring(3);
        assertArrayEquals(data, Base32Codec.decode(withHyphens, crockford));

        // Crockford i/l -> 1 and o -> 0 mapping
        // In Crockford: '1' can be substituted by 'i', 'I', 'l', 'L'; '0' by 'o', 'O'
        String testInput = "01"; // bytes represented by 01 in Crockford
        byte[] decodedExpected = Base32Codec.decode("01", crockford);
        assertArrayEquals(decodedExpected, Base32Codec.decode("oi", crockford));
        assertArrayEquals(decodedExpected, Base32Codec.decode("Ol", crockford));
        assertArrayEquals(decodedExpected, Base32Codec.decode("OL", crockford));
        assertArrayEquals(decodedExpected, Base32Codec.decode("OI", crockford));
    }

    @Test
    @DisplayName("Random binary data round-trip for all variants")
    void testRandomRoundTrip() {
        Random random = new Random(42);
        for (Base32Variant variant : Base32Variant.values()) {
            for (int len = 0; len <= 64; len++) {
                byte[] data = new byte[len];
                random.nextBytes(data);

                // With padding
                String encPad = Base32Codec.encode(data, variant, true);
                byte[] decPad = Base32Codec.decode(encPad, variant);
                assertArrayEquals(data, decPad, "Failed for len " + len + " on variant " + variant);

                // Without padding
                String encNoPad = Base32Codec.encode(data, variant, false);
                byte[] decNoPad = Base32Codec.decode(encNoPad, variant);
                assertArrayEquals(data, decNoPad, "Failed without padding for len " + len + " on " + variant);
            }
        }
    }

    @Test
    @DisplayName("Invalid characters and lengths throw IllegalArgumentException")
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.encode((byte[]) null));
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode(null));

        // Invalid character in RFC 4648 (8 and 9 are not in RFC 4648 standard)
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("MY9====="));

        // Invalid length (unpadded length % 8 == 1 or 3 or 6)
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("M"));
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("MZX"));
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("MZXW6Y"));
    }
}
