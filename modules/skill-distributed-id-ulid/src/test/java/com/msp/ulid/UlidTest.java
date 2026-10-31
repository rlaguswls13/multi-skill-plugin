package com.msp.ulid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UlidTest {

    @Test
    @DisplayName("Generate random ULID")
    void testRandomUlid() {
        long before = System.currentTimeMillis();
        Ulid ulid = Ulid.random();
        long after = System.currentTimeMillis();

        assertNotNull(ulid);
        assertEquals(26, ulid.toString().length());
        assertTrue(ulid.getTimestamp() >= before && ulid.getTimestamp() <= after);
    }

    @Test
    @DisplayName("Round-trip serialization to/from String")
    void testStringRoundTrip() {
        long fixedTimestamp = 1714567890123L;
        Ulid original = Ulid.fromTimestamp(fixedTimestamp);

        String str = original.toString();
        assertEquals(26, str.length());

        Ulid restored = Ulid.fromString(str);
        assertEquals(original, restored);
        assertEquals(fixedTimestamp, restored.getTimestamp());
        assertEquals(str, restored.toString());
    }

    @Test
    @DisplayName("Round-trip serialization to/from 16-byte array")
    void testBytesRoundTrip() {
        Ulid original = Ulid.random();
        byte[] bytes = original.toBytes();
        assertEquals(16, bytes.length);

        Ulid restored = Ulid.fromBytes(bytes);
        assertEquals(original, restored);
    }

    @Test
    @DisplayName("Round-trip to/from UUID")
    void testUuidRoundTrip() {
        Ulid original = Ulid.random();
        UUID uuid = original.toUuid();

        Ulid restored = Ulid.fromUuid(uuid);
        assertEquals(original, restored);
    }

    @Test
    @DisplayName("Crockford Base32 case-insensitivity and alias decoding (I, L -> 1, O -> 0)")
    void testCrockfordAliases() {
        Ulid original = Ulid.random();
        String upper = original.toString();

        // Test lowercase parsing
        String lower = upper.toLowerCase();
        Ulid fromLower = Ulid.fromString(lower);
        assertEquals(original, fromLower);

        // Test alias replacement: replace '1' with 'I' or 'L', and '0' with 'O'
        String withOne = "01ARYZ6S41YYYYYYYYYYYYYYYY";
        String withI = "OIARYZ6S4IYYYYYYYYYYYYYYYY";
        String withL = "0LARYZ6S4LYYYYYYYYYYYYYYYY";

        Ulid u1 = Ulid.fromString(withOne);
        Ulid uI = Ulid.fromString(withI);
        Ulid uL = Ulid.fromString(withL);

        assertEquals(u1, uI);
        assertEquals(u1, uL);
    }

    @Test
    @DisplayName("Lexicographical order matches temporal and bitwise comparison")
    void testLexicographicalOrdering() {
        List<Ulid> list = new ArrayList<>();
        long baseTime = 1700000000000L;

        for (int i = 0; i < 100; i++) {
            list.add(Ulid.fromTimestamp(baseTime + i * 1000));
        }

        for (int i = 0; i < list.size() - 1; i++) {
            Ulid u1 = list.get(i);
            Ulid u2 = list.get(i + 1);

            assertTrue(u1.compareTo(u2) < 0, "u1 must be less than u2 in ULID comparison");
            assertTrue(u1.toString().compareTo(u2.toString()) < 0, "u1 string must be lexicographically less than u2 string");
        }
    }

    @Test
    @DisplayName("Validation and error conditions")
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> Ulid.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> Ulid.fromString("TOO_SHORT"));
        assertThrows(IllegalArgumentException.class, () -> Ulid.fromString("01ARYZ6S41YYYYYYYYYYYYYYY!")); // Illegal char '!'
        assertThrows(IllegalArgumentException.class, () -> Ulid.fromString("01ARYZ6S41YYYYYYYYYYYYYYYU")); // Excluded Crockford char 'U'

        // Overflow: first character > 7 (e.g. '8' -> 8 > 7, exceeding 128-bit)
        assertThrows(IllegalArgumentException.class, () -> Ulid.fromString("80000000000000000000000000"));

        // Invalid byte array length
        assertThrows(IllegalArgumentException.class, () -> Ulid.fromBytes(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> Ulid.fromBytes(new byte[17]));

        // Negative timestamp
        assertThrows(IllegalArgumentException.class, () -> Ulid.fromTimestamp(-1));
    }
}
