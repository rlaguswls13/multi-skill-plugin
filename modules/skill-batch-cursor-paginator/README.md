# 📜 Batch Keyset Cursor Paginator (`skill-batch-cursor-paginator`)

> High-performance, zero-dependency, pure Java 21 Keyset Cursor Pagination utility designed to eliminate deep offset database degradation (`OFFSET N`).

---

## 🌟 Key Features

1. **Keyset / Seek Pagination Pattern**:
   - Replaces slow `OFFSET 100000 LIMIT 20` SQL queries with indexed range queries (`WHERE (created_at, id) < (:sortVal, :id) LIMIT 21`).
2. **Opaque URL-Safe Base64 Cursor Tokens**:
   - Embeds primary key ID, sort column value, and generation timestamp.
   - Robust length-prefixed encoding resisting delimiter collisions and special characters.
3. **Seamless Page Slicing & Query Wrapping**:
   - Automatically detects `hasNext` when querying `pageSize + 1` rows.
   - Trims surplus rows and produces clean `CursorPage<T>` with `nextCursor` and `prevCursor`.
4. **Zero Dependencies**:
   - Pure Java 21 standard library.

---

## 🚀 Quick Start

### 1. Database Query Keyset Pagination

```java
import com.msp.cursor.CursorPaginator;
import com.msp.cursor.CursorPage;
import com.msp.cursor.CursorToken;

// Fetch limit = pageSize + 1 (e.g., 20 + 1 = 21) from SQL / repository
List<Order> orders = orderRepository.findOrdersAfter(cursorToken, 21);

// Wrap into CursorPage
CursorPage<Order> page = CursorPaginator.paginate(
        orders,
        20,
        Order::getId,
        o -> o.getCreatedAt().toString()
);

// Results
List<Order> items = page.items(); // exactly 20 items
boolean hasMore = page.hasNext(); // true
String nextCursor = page.nextCursor(); // Opaque string to pass to client
```

### 2. Decode Cursor on Next Request

```java
if (nextCursor != null) {
    CursorToken token = CursorPaginator.decodeCursor(nextCursor);
    String lastId = token.id();
    String lastSortValue = token.sortValue();
    // Use lastId and lastSortValue in your WHERE clause
}
```
