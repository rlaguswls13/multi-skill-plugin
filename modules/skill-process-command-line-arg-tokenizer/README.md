# skill-process-command-line-arg-tokenizer

Zero-dependency Java 21 cross-platform command-line argument tokenizer and injection-safe shell quoting builder supporting **POSIX** (`sh`/`bash`) and **Windows** (MSVC `CommandLineToArgvW`) rules.

## Architecture Highlights
- **POSIX Dialect (`ShellDialect.POSIX`)**: Supports single quotes, double quotes, backslash escapes, line continuations, adjacent segment concatenation, and strict unterminated-quote detection (`CommandLineParseException`). Quoting uses single-quote wrapping with `'\''` escaping to neutralize `$()`, backticks, globs, and shell metacharacters.
- **Windows Dialect (`ShellDialect.WINDOWS`)**: Implements MSVC `CommandLineToArgvW` backslash-before-quote (`2n` vs `2n+1`) and doubled-quote (`""`) parsing and quoting rules so paths ending in `\` (such as `C:\Program Files\`) round-trip losslessly.
- **Fluent Builder (`CommandLineBuilder`)**: Builds both `List<String>` argument vectors (for `ProcessBuilder`) and escaped command strings.

## Quick Start

```java
import com.msp.cmdtokenizer.*;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        CommandLineTokenizer posix = CommandLineTokenizer.forDialect(ShellDialect.POSIX);
        List<String> tokens = posix.tokenize("git commit -m 'fix: \"quoted\" & $var'");
        System.out.println("Parsed: " + tokens);

        String safeCmd = CommandLineBuilder.forDialect(ShellDialect.WINDOWS)
                .executable("robocopy")
                .arg("C:\\Program Files\\")
                .arg("D:\\Backup Target\\")
                .build();
        System.out.println("Windows cmd: " + safeCmd);
    }
}
```
