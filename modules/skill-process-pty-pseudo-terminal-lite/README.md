# skill-process-pty-pseudo-terminal-lite

> Pure Java lightweight virtual terminal buffer and ANSI escape sequence filter for capturing clean CLI outputs, interactive progress bars, and terminal control codes.

---

## 🚀 Features

- **ANSI / VT100 Escape Code Filtering**:
  - `AnsiEscapeFilter.stripAnsi(text)` strips all ANSI colors (SGR), cursor positions, and OSC window title codes.
- **Terminal Screen Emulation**:
  - `TerminalScreenBuffer` simulates a 2D character grid with configurable width and height (default 80x24).
  - Simulates in-place line rewriting via carriage return (`\r`) for CLI progress bars, spinners, and downloading status indicators.
  - Simulates backspace (`\b`), tab stops (`\t`), line clears (`\033[2K`), and screen clears (`\033[2J`).
  - Supports ANSI cursor navigation (`\033[H`, `\033[r;cH`, up/down/left/right).
  - Automatic vertical scrolling when output exceeds terminal height.
- **Clean Text Extraction**:
  - Extract structured lines with `getLines()`, full output with `getScreenText()`, or inspect specific rows with `getLine(row)`.
- **Pure Java 21**: Zero external dependencies.

---

## 📦 Usage Examples

### 1. Strip ANSI Codes from CLI Logs

```java
import com.msp.ptyterm.AnsiEscapeFilter;

String coloredOutput = "\u001B[31mError:\u001B[0m \u001B[1;33mConnection timed out\u001B[0m";
String clean = AnsiEscapeFilter.stripAnsi(coloredOutput);
System.out.println(clean);
// => "Error: Connection timed out"
```

### 2. Capture Real Output from Rewriting Progress Bars

```java
import com.msp.ptyterm.TerminalScreenBuffer;

TerminalScreenBuffer term = new TerminalScreenBuffer(80, 10);

// CLI writes dynamic progress bar
term.write("Progress: [===>        ] 30%\r");
term.write("Progress: [=======>    ] 70%\r");
term.write("Progress: [============] 100%\n");
term.writeln("Build finished successfully.");

System.out.println(term.getScreenText());
// Output:
// Progress: [============] 100%
// Build finished successfully.
```

---

## 🧪 Testing

```bash
./gradlew :modules:skill-process-pty-pseudo-terminal-lite:test
```
