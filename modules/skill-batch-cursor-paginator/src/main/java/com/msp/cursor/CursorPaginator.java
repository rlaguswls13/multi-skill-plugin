package com.msp.cursor;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * High-performance Keyset Pagination helper to eliminate deep-offset database scan degradation.
 */
public final class CursorPaginator {

    private CursorPaginator() {
        // Utility class
    }

    /**
     * Encodes entity identifier and sort order value into an opaque URL-safe cursor string.
     */
    public static String encodeCursor(String id, String sortValue) {
        return CursorToken.of(id, sortValue).encode();
    }

    /**
     * Encodes entity identifier, sort value, and custom timestamp into an opaque URL-safe cursor string.
     */
    public static String encodeCursor(String id, String sortValue, Instant timestamp) {
        return CursorToken.of(id, sortValue, timestamp).encode();
    }

    /**
     * Decodes an opaque cursor string into a {@link CursorToken}.
     */
    public static CursorToken decodeCursor(String cursor) {
        return CursorToken.decode(cursor);
    }

    /**
     * Produces a {@link CursorPage} from a query result list.
     * <p>
     * When using keyset pagination, database queries typically request {@code pageSize + 1} items.
     * If the fetched list contains more than {@code pageSize} items:
     * <ul>
     *   <li>{@code hasNext} is set to true.</li>
     *   <li>Items are trimmed to exactly {@code pageSize}.</li>
     *   <li>{@code nextCursor} is generated from the last item of the trimmed page.</li>
     * </ul>
     *
     * @param items              List of items retrieved (typically query limit = pageSize + 1)
     * @param pageSize           Desired maximum number of items per page
     * @param idExtractor        Extractor for unique primary key / ID
     * @param sortValueExtractor Extractor for sort order value
     * @param <T>                Item type
     * @return constructed CursorPage
     */
    public static <T> CursorPage<T> paginate(
            List<T> items,
            int pageSize,
            Function<T, String> idExtractor,
            Function<T, String> sortValueExtractor
    ) {
        return paginate(items, pageSize, false, idExtractor, sortValueExtractor);
    }

    /**
     * Produces a {@link CursorPage} with explicit {@code hasPrev} status.
     */
    public static <T> CursorPage<T> paginate(
            List<T> items,
            int pageSize,
            boolean hasPrev,
            Function<T, String> idExtractor,
            Function<T, String> sortValueExtractor
    ) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0, got: " + pageSize);
        }
        Objects.requireNonNull(idExtractor, "idExtractor must not be null");
        Objects.requireNonNull(sortValueExtractor, "sortValueExtractor must not be null");

        if (items == null || items.isEmpty()) {
            return CursorPage.empty();
        }

        boolean hasNext = items.size() > pageSize;
        List<T> pageItems = hasNext ? items.subList(0, pageSize) : items;

        String prevCursor = null;
        if (hasPrev && !pageItems.isEmpty()) {
            T first = pageItems.getFirst();
            prevCursor = encodeCursor(idExtractor.apply(first), sortValueExtractor.apply(first));
        }

        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            T last = pageItems.getLast();
            nextCursor = encodeCursor(idExtractor.apply(last), sortValueExtractor.apply(last));
        }

        return new CursorPage<>(pageItems, nextCursor, prevCursor, hasNext, hasPrev);
    }

    /**
     * Slices an in-memory collection starting strictly after the position represented by {@code cursor}.
     *
     * @param allItems           Full sorted collection of items
     * @param cursor             Opaque cursor string from previous request (null to fetch first page)
     * @param pageSize           Maximum page size
     * @param idExtractor        Entity ID extractor
     * @param sortValueExtractor Entity sort value extractor
     * @param <T>                Item type
     * @return sliced CursorPage
     */
    public static <T> CursorPage<T> paginateSlice(
            List<T> allItems,
            String cursor,
            int pageSize,
            Function<T, String> idExtractor,
            Function<T, String> sortValueExtractor
    ) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0, got: " + pageSize);
        }
        Objects.requireNonNull(idExtractor, "idExtractor must not be null");
        Objects.requireNonNull(sortValueExtractor, "sortValueExtractor must not be null");

        if (allItems == null || allItems.isEmpty()) {
            return CursorPage.empty();
        }

        int startIndex = 0;
        boolean hasPrev = false;

        if (cursor != null && !cursor.isBlank()) {
            CursorToken token = decodeCursor(cursor);
            int matchIndex = -1;
            for (int i = 0; i < allItems.size(); i++) {
                if (Objects.equals(idExtractor.apply(allItems.get(i)), token.id())) {
                    matchIndex = i;
                    break;
                }
            }

            if (matchIndex != -1) {
                startIndex = matchIndex + 1;
                hasPrev = true;
            }
        }

        if (startIndex >= allItems.size()) {
            return new CursorPage<>(List.of(), null, null, false, hasPrev);
        }

        int endIndex = Math.min(startIndex + pageSize, allItems.size());
        List<T> pageItems = allItems.subList(startIndex, endIndex);
        boolean hasNext = endIndex < allItems.size();

        String prevCursor = null;
        if (hasPrev && !pageItems.isEmpty() && startIndex > 0) {
            T prevItem = allItems.get(startIndex - 1);
            prevCursor = encodeCursor(idExtractor.apply(prevItem), sortValueExtractor.apply(prevItem));
        }

        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            T last = pageItems.getLast();
            nextCursor = encodeCursor(idExtractor.apply(last), sortValueExtractor.apply(last));
        }

        return new CursorPage<>(pageItems, nextCursor, prevCursor, hasNext, hasPrev);
    }
}
