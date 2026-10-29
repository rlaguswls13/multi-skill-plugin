# 🔍 Skill: Text Diff Highlighter (`com.msp.textdiff`)

High-performance, line and word-level text difference engine powered by the Longest Common Subsequence (LCS) algorithm with ANSI terminal and semantic HTML formatters. Pure Java 21 standard library with zero runtime dependencies.

---

## 🌟 Key Features

- **Line-Level LCS Diff**: Accurate insertion, deletion, and line numbering tracking.
- **Word & Token-Level Diff**: Fine-grained inline word and symbol tokenization.
- **ANSI Terminal Formatter**: Color-coded CLI diff output (Red `-` for deletion, Green `+` for insertion).
- **Semantic HTML Formatter**: Clean, accessible HTML output with `<ins>` and `<del>` tags and automatic XSS escaping.

---

## 🚀 Quick Start

### 1. Line-by-Line Diff & ANSI Output

```java
import com.msp.textdiff.TextDiffEngine;
import com.msp.textdiff.LineDiff;
import com.msp.textdiff.AnsiDiffFormatter;
import java.util.List;

String oldDoc = "First line\nSecond line\nThird line";
String newDoc = "First line\nSecond modified line\nThird line\nFourth line";

List<LineDiff> lineDiffs = TextDiffEngine.diffLines(oldDoc, newDoc);

// Print colorized unified diff to terminal
System.out.println(AnsiDiffFormatter.formatLines(lineDiffs));
```

### 2. Word-Level Inline Diff & HTML Formatting

```java
import com.msp.textdiff.TextDiffEngine;
import com.msp.textdiff.WordDiff;
import com.msp.textdiff.HtmlDiffFormatter;
import java.util.List;

String oldText = "The quick brown fox jumps over the lazy dog";
String newText = "The fast brown fox leaps over the lazy cat";

List<WordDiff> wordDiffs = TextDiffEngine.diffWords(oldText, newText);

// Render HTML with <ins> and <del> tags
String html = HtmlDiffFormatter.formatWords(wordDiffs);
// Output: The <del class="diff-del">quick</del><ins class="diff-ins">fast</ins> brown fox ...
```
