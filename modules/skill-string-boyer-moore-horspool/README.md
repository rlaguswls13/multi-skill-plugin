# skill-string-boyer-moore-horspool

> High-performance Boyer-Moore-Horspool substring and subsequence search algorithm for text and byte arrays in pure Java 21 with zero dependencies.

---

## 🚀 Features

- **Horspool Right-to-Left Search**:
  - Precomputes bad-character shift table in $O(M + |\Sigma|)$ time.
  - Skips up to $M$ characters on mismatch, achieving average sub-linear $O(N / M)$ performance.
- **Dual Support**:
  - `CharSequence` / `String` text search with ASCII fast-path and Unicode map.
  - Raw `byte[]` binary search using fixed 256-element jump table.
- **Case-Insensitive Mode**:
  - Full ASCII & Unicode case fold matching without pre-allocating converted strings.
- **Precompiled Reusable Patterns**:
  - `BmhPattern.compile("pattern")` & `BmhBytePattern.compile(bytes)` for amortized table precomputation across millions of queries.
- **Pure Java 21**: Zero 3rd-party dependencies.

---

## 📦 Usage Examples

### 1. Basic Text Search & Find All

```java
import com.msp.bmhsearch.BoyerMooreHorspool;
import java.util.List;

String text = "The quick brown fox jumps over the lazy dog";

// Find first occurrence
int index = BoyerMooreHorspool.indexOf(text, "fox"); // 16

// Case-insensitive search
int ciIndex = BoyerMooreHorspool.indexOfIgnoreCase(text, "BROWN"); // 10

// Find all occurrences
List<Integer> occurrences = BoyerMooreHorspool.findAll("banana bandana banana", "banana");
// => [0, 15]
```

### 2. Fast Binary Byte Array Search

```java
byte[] data = ...; // e.g., network packet or file bytes
byte[] magic = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}; // PNG Header

int magicPos = BoyerMooreHorspool.indexOf(data, magic);
```

### 3. Precompiled Pattern for High Throughput

```java
import com.msp.bmhsearch.BmhPattern;

BmhPattern pattern = BmhPattern.compile("ERROR", true);

for (String logLine : logLines) {
    if (pattern.indexOf(logLine, 0) != -1) {
        processError(logLine);
    }
}
```

---

## 🧪 Testing

```bash
./gradlew :modules:skill-string-boyer-moore-horspool:test
```
