# skill-bitmap-roaring-compressed-lite

Roaring Bitmap Compressed 32-bit Integer Set with automatic 16-bit container switching (`ArrayContainer`, `BitmapContainer`, `RunContainer`) and fast set algebra in pure Java 21.

## Architecture Highlights
- **16-Bit High/Low Partitioning**: Splits 32-bit integers into high 16-bit chunk keys and low 16-bit containers (`0..65535`).
- **Dynamic Container Switching**:
  - `ArrayContainer`: Sorted `short[]` when chunk cardinality $\le 4096$.
  - `BitmapContainer`: Direct `long[1024]` bitset (8 KB) when chunk cardinality $> 4096$.
  - `RunContainer`: Run-length encoded `[start, lengthMinusOne]` pairs when `optimize()` finds contiguous runs more compact than arrays or bitmaps.
- **Fast Set Algebra**: Supports `and`, `or`, `xor`, and `andNot` across arbitrary combinations of container types.

## Usage Example

```java
import com.msp.roaring.*;

RoaringBitmapLite a = new RoaringBitmapLite();
a.addRange(1000, 10_000);
a.optimize(); // compresses contiguous range into a single RunContainer run

RoaringBitmapLite b = RoaringBitmapLite.bitmapOf(500, 1500, 9999);
RoaringBitmapLite intersection = a.and(b);

System.out.println(intersection.cardinality()); // 2 (1500, 9999)
```
