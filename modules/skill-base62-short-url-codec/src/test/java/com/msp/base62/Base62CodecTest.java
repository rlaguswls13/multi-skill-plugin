package com.msp.base62;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Base62CodecTest {

    @Test
    @DisplayName("Should encode and decode long values accurately")
    void testLongRoundtrip() {
        assertEquals("0", Base62Codec.encode(0L));
        assertEquals(0L, Base62Codec.decodeToLong("0"));

        assertEquals("1", Base62Codec.encode(1L));
        assertEquals(1L, Base62Codec.decodeToLong("1"));

        assertEquals("a", Base62Codec.encode(10L));
        assertEquals(10L, Base62Codec.decodeToLong("a"));

        assertEquals("A", Base62Codec.encode(36L));
        assertEquals(36L, Base62Codec.decodeToLong("A"));

        assertEquals("Z", Base62Codec.encode(61L));
        assertEquals(61L, Base62Codec.decodeToLong("Z"));

        assertEquals("10", Base62Codec.encode(62L));
        assertEquals(62L, Base62Codec.decodeToLong("10"));

        long[] testValues = {
                123456789L,
                9876543210L,
                Long.MAX_VALUE / 2,
                Long.MAX_VALUE
        };

        for (long val : testValues) {
            String encoded = Base62Codec.encode(val);
            long decoded = Base62Codec.decodeToLong(encoded);
            assertEquals(val, decoded, "Failed roundtrip for long: " + val);
        }
    }

    @Test
    @DisplayName("Should throw for negative long and long overflow on decode")
    void testLongEdgeCases() {
        assertThrows(IllegalArgumentException.class, () -> Base62Codec.encode(-1L));
        assertThrows(IllegalArgumentException.class, () -> Base62Codec.decodeToLong(""));

        // Value that exceeds Long.MAX_VALUE
        String overflowStr = "7LK4Y52123456789";
        assertThrows(ArithmeticException.class, () -> Base62Codec.decodeToLong(overflowStr));
    }

    @Test
    @DisplayName("Should encode and decode BigInteger values accurately")
    void testBigIntegerRoundtrip() {
        assertEquals("0", Base62Codec.encode(BigInteger.ZERO));
        assertEquals(BigInteger.ZERO, Base62Codec.decodeToBigInteger("0"));

        BigInteger large = new BigInteger("1234567890123456789012345678901234567890");
        String encoded = Base62Codec.encode(large);
        BigInteger decoded = Base62Codec.decodeToBigInteger(encoded);
        assertEquals(large, decoded);

        assertThrows(IllegalArgumentException.class, () -> Base62Codec.encode(BigInteger.valueOf(-42)));
    }

    @Test
    @DisplayName("Should encode and decode byte arrays with preserved leading zeros")
    void testBytesRoundtrip() {
        assertEquals("", Base62Codec.encodeBytes(new byte[0]));
        assertArrayEquals(new byte[0], Base62Codec.decodeBytes(""));

        // Leading zeros
        byte[] withZeros = new byte[]{0, 0, 0, 1, 2, 3};
        String encZeros = Base62Codec.encodeBytes(withZeros);
        assertTrue(encZeros.startsWith("000"));
        byte[] decZeros = Base62Codec.decodeBytes(encZeros);
        assertArrayEquals(withZeros, decZeros);

        // String payload bytes
        byte[] hello = "Hello World Base62".getBytes(StandardCharsets.UTF_8);
        String encHello = Base62Codec.encodeBytes(hello);
        byte[] decHello = Base62Codec.decodeBytes(encHello);
        assertArrayEquals(hello, decHello);
    }

    @Test
    @DisplayName("Should encode UUID to exactly 22 chars and decode back with 100% fidelity")
    void testUuidRoundtrip() {
        // Zero UUID
        UUID zeroUuid = new UUID(0L, 0L);
        String zeroEncoded = Base62Codec.encodeUuid(zeroUuid);
        assertEquals(22, zeroEncoded.length());
        assertEquals("0000000000000000000000", zeroEncoded);
        assertEquals(zeroUuid, Base62Codec.decodeUuid(zeroEncoded));

        // Max bits UUID
        UUID maxUuid = new UUID(-1L, -1L);
        String maxEncoded = Base62Codec.encodeUuid(maxUuid);
        assertEquals(22, maxEncoded.length());
        assertEquals(maxUuid, Base62Codec.decodeUuid(maxEncoded));

        // Random UUIDs
        for (int i = 0; i < 50; i++) {
            UUID original = UUID.randomUUID();
            String encoded = Base62Codec.encodeUuid(original);
            assertEquals(22, encoded.length(), "UUID Base62 string must be 22 chars");
            UUID decoded = Base62Codec.decodeUuid(encoded);
            assertEquals(original, decoded, "UUID mismatch on roundtrip: " + original);
        }
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on invalid Base62 characters")
    void testInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Base62Codec.decodeToLong("abc@123"));
        assertThrows(IllegalArgumentException.class, () -> Base62Codec.decodeToLong("abc-def"));
        assertThrows(IllegalArgumentException.class, () -> Base62Codec.decodeToLong("hello world"));
        assertThrows(IllegalArgumentException.class, () -> Base62Codec.decodeToLong("안녕"));

        assertThrows(IllegalArgumentException.class, () -> Base62Codec.decodeUuid("00000000000000000000000")); // 23 chars
    }
}
