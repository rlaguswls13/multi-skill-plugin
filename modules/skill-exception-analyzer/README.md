# skill-exception-analyzer

Zero-dependency exception analyzer for root cause extraction, cycle-safe cause chain traversal, stack trace condensation, and automatic error category classification for Java 21.

## Features
- **Root Cause & Chain Extraction**: Traverses exception causes to find the root error with full protection against circular cause references.
- **Category Detection**: Accurately classifies exceptions into `NETWORK`, `TIMEOUT`, `SECURITY`, `VALIDATION`, or `UNKNOWN`.
- **Cause Matching**: Easily check with `findCause(t, TargetException.class)` or `matches(t, TargetException.class)`.
- **Stack Trace Condensation**: Collapses noisy internal JDK/reflection frames while highlighting application code frames with `[APP]`.
- **Zero Runtime Dependencies**: 100% pure Java 21 standard library.

## Quick Start

```java
import com.msp.exanalyzer.*;
import java.util.Set;

try {
    // some network or business operation
} catch (Exception ex) {
    // 1. Identify root cause
    Throwable root = ExceptionAnalyzer.getRootCause(ex);

    // 2. Classify error category
    ExceptionCategory category = ExceptionAnalyzer.categorize(ex);
    if (category == ExceptionCategory.NETWORK) {
        // Trigger network retry or fallback
    }

    // 3. Print clean, condensed stack trace
    String condensed = ExceptionAnalyzer.formatCondensedStackTrace(ex, Set.of("com.mycompany"));
    System.err.println(condensed);
}
```
