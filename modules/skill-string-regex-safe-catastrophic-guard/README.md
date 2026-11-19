# skill-string-regex-safe-catastrophic-guard

Regular Expression ReDoS (Catastrophic Backtracking) Safe Matcher with Step & Time Limits in pure Java 21.

## 🚀 Features

- **ReDoS Attack Protection**: Intercepts exponential and polynomial backtracking attacks on vulnerable patterns (e.g. `(a+)+$`, `(x+x+)+y`, `(.*)+`).
- **Step-Counting CharSequence**: Tracks character visits during matching and aborts immediately when step limits are exceeded.
- **Per-Match Execution Deadlines**: Hard time budget enforcement using virtual threads and monotonic clock deadlines.
- **Static Pattern Inspector**: Pre-checks regex syntax for dangerous nested quantifiers and overlapping alternations before compilation.
- **Rich Diagnostic Results**: `RegexMatchResult` provides match status, step counts, duration, and failure cause.
- **Zero Runtime Dependencies**: Pure Java 21 standard library (`java.util.regex.*`, `java.util.concurrent.*`).

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation project(':modules:skill-string-regex-safe-catastrophic-guard')
}
```

## 💡 Quick Start

```java
import com.msp.regexsafe.SafeRegexMatcher;
import com.msp.regexsafe.SafeRegexOptions;
import com.msp.regexsafe.RegexMatchResult;
import java.time.Duration;

public class Example {
    public static void main(String[] args) {
        String userRegex = "(a+)+$";
        String userInput = "aaaaaaaaaaaaaaaaaaaaa!";

        SafeRegexOptions options = SafeRegexOptions.defaults()
                .withMaxSteps(5_000)
                .withTimeout(Duration.ofMillis(200));

        RegexMatchResult result = SafeRegexMatcher.matches(userRegex, userInput, options);

        if (result.isStepLimitExceeded() || result.isTimedOut()) {
            System.err.println("ReDoS attack blocked! Steps evaluated: " + result.stepCount());
        } else if (result.isMatched()) {
            System.out.println("Matched: " + result.matchedText());
        }
    }
}
```
