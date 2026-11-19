# skill-string-rabin-karp-rolling-hash

Zero-dependency Java 21 double-hash Polynomial Rolling Hash engine supporting **single-pattern search**, **multi-pattern equal-length batch search**, **Winnowing document fingerprinting**, and **Content-Defined Chunking (CDC)**.

## Architecture Highlights
- **Collision-Resistant Double Hashing (`RollingHash`, `DoubleHash`)**: Uses two independent large prime moduli (`1_000_000_007`, `1_000_000_009`) and bases (`131_071`, `131_101`) with O(1) window slide for both `CharSequence` and `byte[]`.
- **Exact Substring Search (`RabinKarpSearcher`)**: Single-pattern (`search`, `searchFirst`) and single-pass multi-pattern equal-length batch search (`searchMulti`) with exact verification.
- **Winnowing Fingerprinting (`WinnowingFingerprinter`)**: Schleimer-Wilkerson-Aiken rolling window minimum selection for plagiarism/similarity detection.
- **Content-Defined Chunking (`ContentDefinedChunker`)**: Shift-invariant byte chunk boundary detection for deduplication and delta synchronization.

## Usage Example
```java
import com.msp.rabinkarp.*;
import java.util.List;
import java.util.Map;

List<Integer> hits = RabinKarpSearcher.search("abracadabra", "abra"); // [0, 7]

Map<String, List<Integer>> batch = RabinKarpSearcher.searchMulti(
        "the cat sat on the mat", List.of("cat", "mat", "rat")
);

WinnowingFingerprinter fingerprinter = new WinnowingFingerprinter(5, 4);
List<WinnowingFingerprinter.Fingerprint> fps = fingerprinter.fingerprint("sample document text");

ContentDefinedChunker chunker = new ContentDefinedChunker(64, 256, 1024, 16);
List<ContentDefinedChunker.ChunkBoundary> chunks = chunker.chunk(new byte[4096]);
```
