package com.msp.cursor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CursorPaginator Tests")
class CursorPaginatorTest {

    record Item(String id, String title, String createdAt) {}

    @Nested
    @DisplayName("CursorToken Tests")
    class TokenTests {

        @Test
        @DisplayName("Encodes and decodes token preserving id, sortValue, and timestamp")
        void roundTrip() {
            Instant now = Instant.ofEpochMilli(1730310000000L);
            CursorToken token = CursorToken.of("item-12345", "2026-10-31T12:00:00Z", now);

            String encoded = token.encode();
            assertNotNull(encoded);
            assertFalse(encoded.contains("+"));
            assertFalse(encoded.contains("/"));
            assertFalse(encoded.contains("=")); // URL-safe without padding

            CursorToken decoded = CursorToken.decode(encoded);
            assertEquals(token.id(), decoded.id());
            assertEquals(token.sortValue(), decoded.sortValue());
            assertEquals(token.timestamp(), decoded.timestamp());
        }

        @Test
        @DisplayName("Handles IDs and sortValues containing colons, tabs, and unicode")
        void specialCharactersInFields() {
            CursorToken token = CursorToken.of("user:org:999\tkey", "score:100.5 #tag:🎉");
            String encoded = token.encode();
            CursorToken decoded = CursorToken.decode(encoded);

            assertEquals("user:org:999\tkey", decoded.id());
            assertEquals("score:100.5 #tag:🎉", decoded.sortValue());
        }

        @Test
        @DisplayName("Invalid cursors throw IllegalArgumentException")
        void invalidCursorThrows() {
            assertThrows(IllegalArgumentException.class, () -> CursorToken.decode(null));
            assertThrows(IllegalArgumentException.class, () -> CursorToken.decode(""));
            assertThrows(IllegalArgumentException.class, () -> CursorToken.decode("   "));
            assertThrows(IllegalArgumentException.class, () -> CursorToken.decode("invalid-base64-!@#$"));
        }
    }

    @Nested
    @DisplayName("Keyset Query Pagination Tests")
    class KeysetQueryTests {

        @Test
        @DisplayName("Query limit pageSize + 1 detects hasNext and trims extra row")
        void queryWithNextPage() {
            int pageSize = 3;
            // Simulated DB fetch with limit = 4 (pageSize + 1)
            List<Item> fetched = List.of(
                    new Item("1", "A", "2026-01-01"),
                    new Item("2", "B", "2026-01-02"),
                    new Item("3", "C", "2026-01-03"),
                    new Item("4", "D", "2026-01-04")
            );

            CursorPage<Item> page = CursorPaginator.paginate(fetched, pageSize, Item::id, Item::createdAt);

            assertEquals(3, page.size());
            assertEquals(3, page.items().size());
            assertTrue(page.hasNext());
            assertFalse(page.hasPrev());
            assertNotNull(page.nextCursor());

            // Decoded cursor must match the 3rd item ("3")
            CursorToken token = CursorPaginator.decodeCursor(page.nextCursor());
            assertEquals("3", token.id());
            assertEquals("2026-01-03", token.sortValue());
        }

        @Test
        @DisplayName("Last page with <= pageSize items indicates hasNext = false")
        void lastPage() {
            int pageSize = 3;
            List<Item> fetched = List.of(
                    new Item("1", "A", "2026-01-01"),
                    new Item("2", "B", "2026-01-02")
            );

            CursorPage<Item> page = CursorPaginator.paginate(fetched, pageSize, Item::id, Item::createdAt);

            assertEquals(2, page.size());
            assertFalse(page.hasNext());
            assertNull(page.nextCursor());
        }

        @Test
        @DisplayName("Empty or null list produces empty page")
        void emptyOrNull() {
            CursorPage<Item> p1 = CursorPaginator.paginate(List.of(), 5, Item::id, Item::createdAt);
            assertTrue(p1.isEmpty());
            assertFalse(p1.hasNext());

            CursorPage<Item> p2 = CursorPaginator.paginate(null, 5, Item::id, Item::createdAt);
            assertTrue(p2.isEmpty());
        }

        @Test
        @DisplayName("Invalid pageSize throws IllegalArgumentException")
        void invalidPageSize() {
            assertThrows(IllegalArgumentException.class, () ->
                    CursorPaginator.paginate(List.of(), 0, Item::id, Item::createdAt));
        }
    }

    @Nested
    @DisplayName("In-Memory Slice Iteration Tests")
    class SliceTests {

        @Test
        @DisplayName("Iterating through all items page-by-page visits all elements in order")
        void iterateAllPages() {
            List<Item> allItems = new ArrayList<>();
            for (int i = 1; i <= 25; i++) {
                allItems.add(new Item(String.valueOf(i), "Item-" + i, "2026-10-" + String.format("%02d", i)));
            }

            int pageSize = 7;
            List<Item> accumulated = new ArrayList<>();
            String cursor = null;
            int pageCount = 0;

            do {
                CursorPage<Item> page = CursorPaginator.paginateSlice(
                        allItems,
                        cursor,
                        pageSize,
                        Item::id,
                        Item::createdAt
                );
                accumulated.addAll(page.items());
                cursor = page.nextCursor();
                pageCount++;
                if (pageCount == 1) {
                    assertFalse(page.hasPrev());
                } else {
                    assertTrue(page.hasPrev());
                }
            } while (cursor != null);

            assertEquals(4, pageCount); // 7 + 7 + 7 + 4 = 25 items -> 4 pages
            assertEquals(allItems.size(), accumulated.size());
            assertEquals(allItems, accumulated);
        }
    }
}
