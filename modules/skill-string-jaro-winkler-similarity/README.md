# skill-string-jaro-winkler-similarity

> Pure Java Jaro and Jaro-Winkler string distance and similarity metric for record linkage, fuzzy deduplication, and spelling correction.

---

## 🚀 Features

- **Jaro Similarity Algorithm**:
  - Matching window: $\max(\lfloor \max(|s_1|, |s_2|) / 2 \rfloor - 1, 0)$
  - Half-transpositions calculation:
    $$Sim_{jaro} = \frac{1}{3} \left( \frac{m}{|s_1|} + \frac{m}{|s_2|} + \frac{m - t}{m} \right)$$
- **Jaro-Winkler Extension**:
  - Prefix weighting bonus:
    $$Sim_{winkler} = Sim_{jaro} + L \cdot p \cdot (1 - Sim_{jaro})$$
  - Standard scaling factor $p = 0.1$, prefix length $L \le 4$, boost threshold $0.7$.
- **Configurable Metric Engine**:
  - `JaroWinklerMetric.builder()` allows adjusting prefix length, scaling factor, boost threshold, and case sensitivity.
- **Convenient API**:
  - `JaroWinkler.similarity(s1, s2)`: returns similarity in $[0.0, 1.0]$.
  - `JaroWinkler.jaroSimilarity(s1, s2)`: returns pure Jaro similarity.
  - `JaroWinkler.distance(s1, s2)`: returns $1.0 - \text{similarity}$.
  - `JaroWinkler.isMatch(s1, s2, threshold)`: returns boolean flag.
- **Pure Java 21**: Zero runtime dependencies.

---

## 📦 Usage Examples

### 1. Default Jaro and Jaro-Winkler Similarity

```java
import com.msp.jarowinkler.JaroWinkler;

// Benchmark names: "MARTHA" vs "MARHTA"
double jaroSim = JaroWinkler.jaroSimilarity("MARTHA", "MARHTA"); // ~0.944
double winklerSim = JaroWinkler.similarity("MARTHA", "MARHTA");   // ~0.961
double distance = JaroWinkler.distance("MARTHA", "MARHTA");       // ~0.039

System.out.printf("Jaro: %.3f, Jaro-Winkler: %.3f%n", jaroSim, winklerSim);

// Record linkage threshold check
if (JaroWinkler.isMatch("DIXON", "DICKSONX", 0.80)) {
    System.out.println("Candidate record matched!");
}
```

### 2. Custom Metric Builder (e.g. Case-Insensitive)

```java
import com.msp.jarowinkler.JaroWinkler;
import com.msp.jarowinkler.JaroWinklerMetric;

JaroWinklerMetric metric = JaroWinkler.builder()
        .caseSensitive(false)
        .scalingFactor(0.1)
        .maxPrefixLength(4)
        .boostThreshold(0.7)
        .build();

double score = metric.similarity("john.doe", "JOHN.DOE"); // 1.0
```
