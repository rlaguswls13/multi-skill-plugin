package com.msp.base91;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("basE91 Binary-to-ASCII Codec Tests")
class Base91Test {

    @Test
    @DisplayName("Empty data encodes and decodes to empty")
    void testEmpty() {
        assertEquals("", Base91.encode(new byte[0]));
        assertArrayEquals(new byte[0], Base91.decode(""));
        assertEquals("", Base91.encode(null));
        assertArrayEquals(new byte[0], Base91.decode((String) null));
    }

    @Test
    @DisplayName("Known strings encode and decode identically")
    void testKnownStringsRoundtrip() {
        String[] samples = {
                "test",
                "Hello, World!",
                "basE91 is a high-density binary-to-ASCII encoding method.",
                "1234567890!@#$%^&*()_+",
                "The quick brown fox jumps over the lazy dog."
        };

        for (String sample : samples) {
            byte[] original = sample.getBytes(StandardCharsets.UTF_8);
            String encoded = Base91.encode(original);
            assertNotNull(encoded);
            assertFalse(encoded.isEmpty());

            byte[] decoded = Base91.decode(encoded);
            assertArrayEquals(original, decoded, "Mismatch for: " + sample);
            assertEquals(sample, new String(decoded, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("Full byte range 0x00..0xFF roundtrips correctly")
    void testAllByteValues() {
        byte[] allBytes = new byte[256];
        for (int i = 0; i < 256; i++) {
            allBytes[i] = (byte) i;
        }

        String encoded = Base91.encode(allBytes);
        byte[] decoded = Base91.decode(encoded);

        assertArrayEquals(allBytes, decoded);
    }

    @Test
    @DisplayName("basE91 encoding is strictly more compact than standard Base64")
    void testHigherDensityThanBase64() {
        byte[] randomData = new byte[2048];
        new Random(42).nextBytes(randomData);

        String b91 = Base91.encode(randomData);
        String b64 = Base64.getEncoder().encodeToString(randomData);

        assertTrue(b91.length() < b64.length(),
                String.format("basE91 (%d chars) should be smaller than Base64 (%d chars)", b91.length(), b64.length()));

        byte[] decoded = Base91.decode(b91);
        assertArrayEquals(randomData, decoded);
    }

    @Test
    @DisplayName("Large random binary payloads roundtrip cleanly")
    void testLargeBinaryPayload() {
        byte[] large = new byte[65536]; // 64 KB
        new Random(1337).nextBytes(large);

        String encoded = Base91.encode(large);
        byte[] decoded = Base91.decode(encoded);

        assertArrayEquals(large, decoded);
    }

    @Test
    @DisplayName("Base91OutputStream and Base91InputStream stream pipeline roundtrips")
    void testStreamingPipeline() throws Exception {
        byte[] testData = "Streaming data test with basE91 OutputStream and InputStream!".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Base91OutputStream b91Out = new Base91OutputStream(baos)) {
            b91Out.write(testData);
        }

        byte[] encodedAscii = baos.toByteArray();
        assertEquals(Base91.encode(testData), new String(encodedAscii, StandardCharsets.US_ASCII));

        ByteArrayInputStream bais = new ByteArrayInputStream(encodedAscii);
        ByteArrayOutputStream decodedBaos = new ByteArrayOutputStream();
        try (Base91InputStream b91In = new Base91InputStream(bais)) {
            b91In.transferTo(decodedBaos);
        }

        assertArrayEquals(testData, decodedBaos.toByteArray());
    }

    @Test
    @DisplayName("Decoder ignores embedded whitespace (newlines, carriage returns, spaces)")
    void testDecoderIgnoresWhitespace() {
        byte[] original = "Ignore whitespaces test".getBytes(StandardCharsets.UTF_8);
        String encoded = Base91.encode(original);

        // Inject whitespace
        String formatted = "\n  " + encoded.substring(0, 5) + " \r\n " + encoded.substring(5) + "\n\t";
        byte[] decoded = Base91.decode(formatted);

        assertArrayEquals(original, decoded);
    }

    @Test
    @DisplayName("Decoder rejects invalid characters")
    void testInvalidCharacterThrowsException() {
        // '-' is not in the basE91 alphabet
        assertThrows(IllegalArgumentException.class, () -> Base91.decode("AB-CD"));
    }
}
