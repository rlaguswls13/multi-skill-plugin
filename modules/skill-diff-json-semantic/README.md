# Semantic JSON Diff Engine (`skill-diff-json-semantic`)

Zero-dependency semantic JSON diff engine in pure Java 21. Detects field addition, deletion, modification, and nested array/object variations, providing structured diff records and readable change summaries.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Deep Recursive Comparison**: Compares nested JSON objects and arrays by dot/bracket paths (`user.profile.city`, `items[0].price`).
- **Semantic Equivalence**: Treats numerically equivalent values (e.g., `42` vs `42.0`) and out-of-order keys as identical.
- **Direct Map Support**: Compare raw JSON strings or existing `Map<String, Object>` models directly.
- **Human-Readable Summaries**: Generates readable change reports with clear diff badges (`[+] ADDED`, `[-] REMOVED`, `[~] MODIFIED`).

---

## 📦 Usage Examples

### 1. Computing Semantic Differences

```java
import com.msp.jsondiff.SemanticJsonDiff;
import com.msp.jsondiff.JsonDiffEntry;
import java.util.List;

String before = """
    {
      "name": "Alice",
      "age": 30,
      "status": "PENDING"
    }
    """;

String after = """
    {
      "name": "Alice",
      "age": 31,
      "email": "alice@example.com"
    }
    """;

List<JsonDiffEntry> diffs = SemanticJsonDiff.diff(before, after);

// Check if any changes exist
boolean changed = SemanticJsonDiff.hasChanges(before, after); // true

// Print human-readable summary
System.out.println(SemanticJsonDiff.toSummaryString(diffs));
```

Output:
```text
[+] ADDED    email => "alice@example.com"
[-] REMOVED  status <= "PENDING"
[~] MODIFIED age: 30 => 31
```

### 2. Inspecting Individual Diff Entries

```java
for (JsonDiffEntry entry : diffs) {
    System.out.printf("Path: %s, Type: %s, Old: %s, New: %s%n",
        entry.path(), entry.type(), entry.oldValue(), entry.newValue());
}
```
