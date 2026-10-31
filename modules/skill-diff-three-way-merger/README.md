# 🔀 Three-Way Diff Merger (`skill-diff-three-way-merger`)

A pure Java 21 implementation of Git-style 3-way merge (`base`, `ours`, `theirs`) with automatic change reconciliation and standard conflict markers.

---

## ✨ Features

- **Zero Runtime Dependencies**: Pure Java 21 using standard LCS (Longest Common Subsequence) diff chunking.
- **Git-Style Semantics**:
  - Unilateral changes adopted cleanly.
  - Independent changes in different file regions merged cleanly.
  - Identical changes in both branches merged cleanly.
  - Overlapping conflicting edits marked with standard `<<<<<<< OURS`, `=======`, `>>>>>>> THEIRS`.
- **Dual String & List APIs**: Works directly on `String` or pre-tokenized `List<String>`.
- **Chunk-Level Inspection**: Inspect fine-grained `MergeChunk` records indicating conflicts per region.

---

## 🚀 Quick Start

### 1. Simple 3-Way Merge

```java
import com.msp.threewaydiff.ThreeWayMerger;
import com.msp.threewaydiff.MergeResult;

String base = "Title\nOld Body\nFooter";
String ours = "Title\nNew Ours Body\nFooter";
String theirs = "Title\nOld Body\nFooter\nCopyright 2026";

MergeResult result = ThreeWayMerger.merge(base, ours, theirs);

if (!result.hasConflicts()) {
    System.out.println("Merge Succeeded:\n" + result.formattedOutput());
} else {
    System.err.println("Conflicts detected:\n" + result.formattedOutput());
}
```

### 2. Handling Conflicts

When conflicting modifications occur on the same region:

```
<<<<<<< OURS
New Ours Body
=======
New Theirs Body
>>>>>>> THEIRS
```

---

## 🛠️ Testing

```bash
./gradlew :modules:skill-diff-three-way-merger:test
```
