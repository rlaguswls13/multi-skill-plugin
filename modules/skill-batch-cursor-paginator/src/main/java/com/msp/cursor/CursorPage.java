package com.msp.cursor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Immutable page container representing a keyset cursor-paginated result slice.
 *
 * @param items      Current page items
 * @param nextCursor Opaque cursor pointing to the last item of this page (null if hasNext is false)
 * @param prevCursor Opaque cursor pointing to the start of this page (null if hasPrev is false)
 * @param hasNext    True if there are subsequent items available
 * @param hasPrev    True if there are preceding items available
 * @param <T>        Item type
 */
public record CursorPage<T>(
        List<T> items,
        String nextCursor,
        String prevCursor,
        boolean hasNext,
        boolean hasPrev
) implements Serializable {

    public CursorPage {
        items = (items == null) ? List.of() : Collections.unmodifiableList(List.copyOf(items));
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, null, false, false);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Optional<T> firstItem() {
        return items.isEmpty() ? Optional.empty() : Optional.of(items.getFirst());
    }

    public Optional<T> lastItem() {
        return items.isEmpty() ? Optional.empty() : Optional.of(items.getLast());
    }

    public Optional<String> getNextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    public Optional<String> getPrevCursor() {
        return Optional.ofNullable(prevCursor);
    }
}
