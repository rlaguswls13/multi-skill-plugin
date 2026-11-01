# Word-Level Text Diff Engine (`skill-diff-text-word-level`)

Fine-grained word, whitespace, and punctuation level text difference engine computing Shortest Edit Scripts (SES) with inline markup rendering.

## Features
- **Tokenization**: Preserves words, whitespace sequences, numbers, and Unicode punctuation marks.
- **SES Algorithm**: Utilizes prefix/suffix trimming combined with Myers' Shortest Edit Script diff algorithm for optimal change detection.
- **Multilingual Support**: First-class support for English, Korean (Hangul), Japanese, Chinese, and Unicode symbols.
- **Inline & Custom Markup**:
  - Built-in inline markup: `"[-deleted-]"` and `"{+inserted+}"`.
  - Custom markup: configure HTML `<del>`/`<ins>` or any arbitrary formatting tags.
- **Zero Dependencies**: Pure Java 21 standard library.

## Usage

```java
import com.msp.worddiff.WordDiffItem;
import com.msp.worddiff.WordLevelDiff;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        String original = "The quick brown fox";
        String revised = "The slow brown fox";

        // 1. Generate inline diff markup
        String inline = WordLevelDiff.toInlineMarkup(original, revised);
        System.out.println(inline);
        // Output: "The [-quick-]{+slow+} brown fox"

        // 2. Generate HTML markup
        String html = WordLevelDiff.toCustomMarkup(
                original, revised,
                "<del>", "</del>",
                "<ins>", "</ins>"
        );
        System.out.println(html);
        // Output: "The <del>quick</del><ins>slow</ins> brown fox"

        // 3. Inspect raw diff items
        List<WordDiffItem> diffs = WordLevelDiff.diffWords(original, revised);
        for (WordDiffItem item : diffs) {
            System.out.println(item.op() + " -> " + item.text());
        }
    }
}
```
