package com.msp.objpool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ObjectPoolTest {

    @Test
    @DisplayName("Borrow and release ByteBuffer from pool")
    void testBorrowRelease() {
        ObjectPool<ByteBuffer> pool = ObjectPool.builder(() -> ByteBuffer.allocate(1024))
                .reset(ByteBuffer::clear)
                .maxSize(2)
                .build();

        assertEquals(0, pool.activeCount());

        try (Pooled<ByteBuffer> p1 = pool.borrow()) {
            ByteBuffer buf1 = p1.get();
            buf1.put((byte) 42);
            assertEquals(1, pool.activeCount());

            try (Pooled<ByteBuffer> p2 = pool.borrow()) {
                assertEquals(2, pool.activeCount());
            }

            assertEquals(1, pool.activeCount());
        }

        assertEquals(0, pool.activeCount());
        assertEquals(2, pool.idleCount());

        // Borrow again to ensure resetHandler (clear) was applied
        try (Pooled<ByteBuffer> p = pool.borrow()) {
            assertEquals(0, p.get().position());
        }
    }

    @Test
    @DisplayName("withObject functional helper test")
    void testWithObject() {
        ObjectPool<StringBuilder> pool = ObjectPool.builder(StringBuilder::new)
                .reset(sb -> sb.setLength(0))
                .maxSize(4)
                .build();

        String res = pool.withObject(sb -> {
            sb.append("Hello ").append("World");
            return sb.toString();
        });

        assertEquals("Hello World", res);
        assertEquals(0, pool.activeCount());
    }

    @Test
    @DisplayName("Borrow timeout when pool exhausted")
    void testPoolExhaustedTimeout() {
        ObjectPool<String> pool = ObjectPool.builder(() -> "resource")
                .maxSize(1)
                .build();

        Pooled<String> p1 = pool.borrow();
        assertThrows(RuntimeException.class, () -> pool.borrow(Duration.ofMillis(50)));
        p1.close();
    }
}
