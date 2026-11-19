# skill-string-suffix-array-lcp-builder

Zero-dependency Java 21 Suffix Array (`O(N log N)` prefix doubling) and Kasai LCP (`O(N)`) string-indexing engine.

## Architecture Highlights
- **Prefix-Doubling Radix Sort**: Builds the suffix array in `O(N log N)` worst-case time without object boxing.
- **Kasai LCP Algorithm**: Computes adjacent suffix longest common prefixes in linear `O(N)` time.
- **Binary Search Pattern Matching**: `countOccurrences(pattern)` and `findOccurrences(pattern)` locate all matches in `O(M log N)` time.
- **Analytics & Generalized Suffix Array**: Supports `countDistinctSubstrings()`, `longestRepeatedSubstring()`, `longestRepeatedSubstrings()`, and collision-free `longestCommonSubstring(first, second)` using a negative sentinel separator.

## Quick Start

```java
import com.msp.suffixarray.SuffixArray;

public class Example {
    public static void main(String[] args) {
        SuffixArray sa = SuffixArray.build("banana");
        System.out.println("Occurrences of 'ana': " + sa.findOccurrences("ana")); // [1, 3]
        System.out.println("Longest repeated: " + sa.longestRepeatedSubstring()); // ana
        System.out.println("Distinct substrings: " + sa.countDistinctSubstrings()); // 15

        String lcs = SuffixArray.longestCommonSubstring("fast_algorithm_v1", "my_algorithm_impl");
        System.out.println("Longest common substring: " + lcs); // _algorithm_
    }
}
```
