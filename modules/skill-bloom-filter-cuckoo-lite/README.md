# Cuckoo Filter Lite (`skill-bloom-filter-cuckoo-lite`)

Zero-dependency, high-performance Cuckoo Filter in Java 21 supporting fast item insertion, lookup, and true item **deletion**.

## Features

- **True Item Deletion**: Unlike standard Bloom Filters, Cuckoo Filters allow removing entries (`remove(T)`) without rebuild or counting overhead.
- **High Space Efficiency & Low FPR**: Uses 4-way set associative buckets with 16-bit fingerprints, yielding false positive rates $< 0.01\%$.
- **Cuckoo Displacement**: Implements partial-key cuckoo hashing ($i_2 = i_1 \oplus \text{hash}(f)$) with random eviction kickouts up to configurable max kicks.
- **Thread-Safe Variant**: Provides `ConcurrentCuckooFilter<T>` with read-write concurrency.
- **Extensible Hashing**: Built-in hashers for strings, byte arrays, numbers, or custom objects via `Hasher<T>`.

## Runnable Code Example

```java
import com.msp.cuckoo.CuckooFilter;

public class CuckooExample {
    public static void main(String[] args) {
        // 1. Create a Cuckoo Filter with expected capacity of 10,000 items
        CuckooFilter<String> filter = CuckooFilter.createForStrings(10_000);

        // 2. Insert items
        filter.put("user_101");
        filter.put("user_102");

        // 3. Query membership
        System.out.println("Contains user_101: " + filter.contains("user_101")); // true
        System.out.println("Contains user_999: " + filter.contains("user_999")); // false

        // 4. Delete item
        boolean removed = filter.remove("user_101");
        System.out.println("Removed user_101: " + removed);                       // true
        System.out.println("Contains user_101: " + filter.contains("user_101")); // false

        // 5. Inspect load factor
        System.out.println("Count: " + filter.getCount());
        System.out.println("Load Factor: " + String.format("%.2f%%", filter.getLoadFactor() * 100));
    }
}
```
