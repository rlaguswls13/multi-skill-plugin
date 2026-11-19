# skill-string-knuth-morris-pratt-automaton

Knuth-Morris-Pratt (KMP) Failure-Function Automaton for Zero-Backtracking $O(N)$ Streaming Pattern Search in pure Java 21.

## Features
- **$O(M)$ LPS Failure Table Precomputation**: Computes the Longest Proper Prefix which is also a Suffix (`lpsTable()`) once during compilation.
- **Zero-Backtracking $O(N)$ Search**: Never re-examines previously scanned characters or bytes, making it ideal for unbuffered network/file streams.
- **Multi-Source Support**: Searches across `CharSequence`, `char[]`, `byte[]`, `Reader`, and `InputStream` without buffering the whole stream in memory.
- **Rich Match APIs**: `indexOf`, `findAll` (overlapping & non-overlapping), `countMatches`, and lazy `streamMatches(Reader)` / `streamMatches(InputStream)`.

## Usage Example

```java
package com.msp.kmp.example;

import com.msp.kmp.KmpAutomaton;
import java.io.StringReader;
import java.util.Arrays;

public class KmpExample {
    public static void main(String[] args) {
        KmpAutomaton kmp = KmpAutomaton.compile("ABAB");
        System.out.println("LPS Failure Table: " + Arrays.toString(kmp.lpsTable()));

        String text = "ABABABABCABAB";
        System.out.println("First indexOf: " + kmp.indexOf(text));
        System.out.println("Overlapping findAll: " + kmp.findAll(text, true));
        System.out.println("Non-overlapping findAll: " + kmp.findAll(text, false));

        // Zero-backtracking streaming Reader search
        long[] positions = kmp.streamMatches(new StringReader(text)).toArray();
        System.out.println("Streamed match offsets: " + Arrays.toString(positions));
    }
}
```
