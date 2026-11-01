package com.msp.quotient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QuotientFilterTest {

    @Test
    @DisplayName("Empty filter properties")
    void testEmptyFilter() {
        QuotientFilter<String> qf = QuotientFilter.ofStrings(8, 16);
        assertEquals(0, qf.getCount());
        assertTrue(qf.isEmpty());
        assertEquals(256, qf.getCapacity());
        assertEquals(0.0, qf.getLoadFactor());
        assertFalse(qf.contains("apple"));
        assertFalse(qf.remove("apple"));
    }

    @Test
    @DisplayName("Put and Contains basic operations")
    void testPutAndContains() {
        QuotientFilter<String> qf = QuotientFilter.ofStrings(10, 16);

        assertTrue(qf.put("user_101"));
        assertTrue(qf.put("user_102"));
        assertTrue(qf.put("user_103"));

        assertEquals(3, qf.getCount());
        assertFalse(qf.isEmpty());

        assertTrue(qf.contains("user_101"));
        assertTrue(qf.contains("user_102"));
        assertTrue(qf.contains("user_103"));
        assertFalse(qf.contains("user_999"));

        // Duplicate insertion
        assertFalse(qf.put("user_101"));
        assertEquals(3, qf.getCount());
    }

    @Test
    @DisplayName("True Element Deletion (Single and Multiple in Run)")
    void testDeletion() {
        QuotientFilter<String> qf = QuotientFilter.ofStrings(8, 16);

        qf.put("alpha");
        qf.put("beta");
        qf.put("gamma");
        qf.put("delta");

        assertEquals(4, qf.getCount());

        // Delete "beta"
        assertTrue(qf.remove("beta"));
        assertEquals(3, qf.getCount());
        assertFalse(qf.contains("beta"));

        // Others must still be present
        assertTrue(qf.contains("alpha"));
        assertTrue(qf.contains("gamma"));
        assertTrue(qf.contains("delta"));

        // Removing non-existent
        assertFalse(qf.remove("beta"));
        assertFalse(qf.remove("non_existent"));

        // Re-inserting beta
        assertTrue(qf.put("beta"));
        assertEquals(4, qf.getCount());
        assertTrue(qf.contains("beta"));
    }

    @Test
    @DisplayName("Quotient hash collision with identical quotient and differing remainders")
    void testDirectHashCollisions() {
        QuotientFilter<Integer> qf = QuotientFilter.create(6, 16); // 64 slots, 16-bit remainder

        // Construct 3 hashes that share the same quotient (e.g. quotient = 10)
        int quotient = 10;
        int rBits = 16;
        long rem1 = 100L;
        long rem2 = 200L;
        long rem3 = 300L;

        long hash1 = ((long) quotient << rBits) | rem1;
        long hash2 = ((long) quotient << rBits) | rem2;
        long hash3 = ((long) quotient << rBits) | rem3;

        assertTrue(qf.putHash(hash2));
        assertTrue(qf.putHash(hash1)); // out of order insertion (should be sorted in run)
        assertTrue(qf.putHash(hash3));

        assertEquals(3, qf.getCount());

        assertTrue(qf.containsHash(hash1));
        assertTrue(qf.containsHash(hash2));
        assertTrue(qf.containsHash(hash3));

        // Delete middle remainder
        assertTrue(qf.removeHash(hash2));
        assertFalse(qf.containsHash(hash2));
        assertTrue(qf.containsHash(hash1));
        assertTrue(qf.containsHash(hash3));
        assertEquals(2, qf.getCount());

        // Delete first remainder
        assertTrue(qf.removeHash(hash1));
        assertFalse(qf.containsHash(hash1));
        assertTrue(qf.containsHash(hash3));
        assertEquals(1, qf.getCount());

        // Delete last remainder (run becomes empty)
        assertTrue(qf.removeHash(hash3));
        assertFalse(qf.containsHash(hash3));
        assertEquals(0, qf.getCount());
    }

    @Test
    @DisplayName("Bulk insert and selective deletion with high load")
    void testBulkInsertAndSelectiveDelete() {
        QuotientFilter<String> qf = QuotientFilter.ofStrings(12, 16); // 4096 slots

        int total = 1000;
        Set<String> inserted = new HashSet<>();
        for (int i = 0; i < total; i++) {
            String item = "item_key_" + i;
            inserted.add(item);
            assertTrue(qf.put(item));
        }

        assertEquals(total, qf.getCount());

        // Verify all 1000 exist
        for (String item : inserted) {
            assertTrue(qf.contains(item), "Should contain " + item);
        }

        // Delete half
        int removedCount = 0;
        for (int i = 0; i < total; i += 2) {
            String item = "item_key_" + i;
            assertTrue(qf.remove(item));
            removedCount++;
        }

        assertEquals(total - removedCount, qf.getCount());

        // Verify state
        for (int i = 0; i < total; i++) {
            String item = "item_key_" + i;
            if (i % 2 == 0) {
                assertFalse(qf.contains(item), "Deleted item should not exist: " + item);
            } else {
                assertTrue(qf.contains(item), "Remaining item should exist: " + item);
            }
        }
    }

    @Test
    @DisplayName("Clear resets all states")
    void testClear() {
        QuotientFilter<String> qf = QuotientFilter.ofStrings(8, 16);
        for (int i = 0; i < 50; i++) {
            qf.put("elem_" + i);
        }
        assertEquals(50, qf.getCount());

        qf.clear();

        assertEquals(0, qf.getCount());
        assertTrue(qf.isEmpty());
        for (int i = 0; i < 50; i++) {
            assertFalse(qf.contains("elem_" + i));
        }
    }

    @Test
    @DisplayName("Factory with expected capacity and target FPP")
    void testWithCapacity() {
        QuotientFilter<Integer> qf = QuotientFilter.withCapacity(500, 0.01);
        assertTrue(qf.getCapacity() >= 500);
        assertTrue(qf.getRemainderBits() >= 7);

        for (int i = 0; i < 400; i++) {
            qf.put(i);
        }

        for (int i = 0; i < 400; i++) {
            assertTrue(qf.contains(i));
        }

        // Check false positive rate on 10,000 non-existing elements
        int falsePositives = 0;
        for (int i = 10000; i < 20000; i++) {
            if (qf.contains(i)) {
                falsePositives++;
            }
        }

        double observedFpp = (double) falsePositives / 10000;
        assertTrue(observedFpp < 0.05, "Observed FPP " + observedFpp + " should be under 5%");
    }
}
