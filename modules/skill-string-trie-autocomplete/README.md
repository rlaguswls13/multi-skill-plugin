# 🧩 skill-string-trie-autocomplete

> Top-K prefix autocomplete and frequency/weight ranking Trie engine supporting Unicode (English, Korean, CJK) with zero 3rd-party dependencies.

---

## 🌟 Key Features

- **Top-K Prefix Ranking**: Fast prefix subtree search returning the highest-scoring suggestions.
- **Dynamic Frequency & Weights**: Adjust word popularity in real time with `incrementScore(word, delta)` or `insert(word, score)`.
- **Full Unicode Support**: Operates on Unicode code points, natively supporting multilingual text including Korean Hangul syllables, CJK, and accented characters.
- **Thread Safety**: Concurrent readers and writers protected by `ReentrantReadWriteLock`.
- **Empty Prefix Support**: Supplying an empty or null prefix returns the globally top-ranked words.
- **Zero Runtime Dependencies**: Pure Java 21 standard library.

---

## 🚀 Quick Start

```java
import com.msp.autocomplete.AutocompleteTrie;
import com.msp.autocomplete.AutocompleteItem;
import java.util.List;

AutocompleteTrie trie = new AutocompleteTrie();

// Populate terms with relevance/popularity scores
trie.insert("한국", 100.0);
trie.insert("한국어", 150.0);
trie.insert("한류", 80.0);
trie.insert("한복", 70.0);

// Increment weight based on user search clicks
trie.incrementScore("한국어", 10.0);

// Autocomplete suggestions matching prefix "한"
List<AutocompleteItem> suggestions = trie.suggest("한", 3);
for (AutocompleteItem item : suggestions) {
    System.out.printf("%s (score: %.1f)%n", item.text(), item.score());
}
// Output:
// 한국어 (score: 160.0)
// 한국 (score: 100.0)
// 한류 (score: 80.0)
```

---

## 🧪 Testing

```bash
.\gradlew.bat :modules:skill-string-trie-autocomplete:test
```
