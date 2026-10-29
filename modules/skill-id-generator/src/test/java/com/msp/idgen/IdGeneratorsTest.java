package com.msp.idgen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdGeneratorsTest {

    @Test
    @DisplayName("IdGenerators facade should produce valid IDs for all types")
    void testFacadeMethods() {
        assertNotNull(IdGenerators.snowflake());
        assertTrue(IdGenerators.snowflake().nextId() > 0);

        assertNotNull(IdGenerators.snowflake(15));
        assertTrue(IdGenerators.snowflake(15).nextId() > 0);

        assertNotNull(IdGenerators.tsid());
        assertEquals(13, IdGenerators.tsid().nextTsid().length());

        assertEquals(21, IdGenerators.nanoId().length());
        assertEquals(12, IdGenerators.nanoId(12).length());

        String uuidV4 = IdGenerators.uuidV4();
        assertEquals(36, uuidV4.length());
        assertEquals(UUID.fromString(uuidV4).toString(), uuidV4);

        String uuidV4NoDashes = IdGenerators.uuidV4NoDashes();
        assertEquals(32, uuidV4NoDashes.length());
        assertFalse(uuidV4NoDashes.contains("-"));
    }

    @Test
    @DisplayName("UUIDv7 must conform to RFC 9562 version 7 and variant 2")
    void testUuidV7Compliance() {
        long beforeMs = System.currentTimeMillis();
        UUID uuid = IdGenerators.uuidV7();
        long afterMs = System.currentTimeMillis();

        assertEquals(7, uuid.version(), "UUID version must be 7");
        assertEquals(2, uuid.variant(), "UUID variant must be 2 (IETF Leach-Salz / RFC 4122/9562)");

        // Extract 48-bit timestamp from the top 48 bits of MSB
        long extractedTimestamp = uuid.getMostSignificantBits() >>> 16;
        assertTrue(extractedTimestamp >= beforeMs && extractedTimestamp <= afterMs,
                "UUIDv7 timestamp must fall within current time window");
    }
}
