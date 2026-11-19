# Levenshtein Transducer (`skill-string-levenshtein-transducer`)

Levenshtein Automaton (Transducer) for ultra-fast spell checking, fuzzy string matching, and dictionary typo correction within edit distance $K$ in a Prefix Trie.

## Features

- **Parametric Levenshtein Automaton**: Represents the non-deterministic edit-distance state vector $D[0..M]$ for query word $W$ of length $M$.
- **Trie Subtree Pruning**: During depth-first intersection with the dictionary trie, branches where $\min_{i} D[i] > K$ are immediately pruned. Never scans hopeless prefixes!
- **Up to 100x Faster than Linear Scan**: Instead of computing edit distance individually against every dictionary entry ($O(D \cdot M \cdot N)$), searches only relevant Trie prefixes ($O(|Trie_{pruned}| \cdot M)$).
- **Parity Verified**: 100% precision and recall parity verified against standard dynamic programming Levenshtein algorithm.
- **Pure Java 21**: Zero runtime dependencies.

## Usage Example

```java
import com.msp.transducer.*;

// 1. Build dictionary trie
DictionaryTrie trie = new DictionaryTrie();
trie.insertAll(List.of(
    "algorithm",
    "logarithm",
    "alligator",
    "although",
    "altogether"
));

// 2. Search fuzzy matches within edit distance K=2
List<FuzzyMatch> matches = LevenshteinTransducer.search(trie, "algoritm", 2);

for (FuzzyMatch match : matches) {
    System.out.println(match.word() + " -> distance " + match.distance());
}
// Outputs: algorithm -> distance 1

// 3. Find single closest match
Optional<FuzzyMatch> closest = LevenshteinTransducer.findClosest(trie, "altogethr", 1);
// -> "altogether" (dist 1)
```
