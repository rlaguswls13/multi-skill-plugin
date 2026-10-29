# 🌸 skill-bloom-filter-lite

> **BitSet-based memory-efficient Bloom filter for fast negative set membership tests**  
> Pure Java 21 zero-dependency library providing optimal size calculation, 0% false negative guarantee, and Kirsch-Mitzenmacher double-hashing using 128-bit MurmurHash3.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library (`java.util.BitSet`, `ReentrantReadWriteLock`).
- **Guaranteed 0% False Negatives**: If `mightContain(element)` returns `false`, the element is guaranteed absent from the set.
- **Double-Hashing Optimization**:
  - Uses two 64-bit halves of a 128-bit MurmurHash3 ($h_1$ and $h_2$) to simulate $k$ independent hash functions:
    $$g_i(x) = (h_1 + i \cdot h_2) \pmod m$$
  - Avoids computing $k$ separate cryptographic hashes, giving stellar throughput.
- **Automatic Optimal Sizing**:
  - Automatically computes mathematically optimal bit vector size $m$ and hash count $k$ given target capacity $n$ and desired false positive probability $p$.
- **Customizable Funnels**: Built-in funnels for `String`, `Long`, `Integer`, `byte[]`, with extensible `Funnel<T>` interface for custom domain entities.
- **Thread-Safe**: Read-write lock protection allows high-throughput concurrent readers with safe writes.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-bloom-filter-lite:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Database Cache Penetration Guard (Filter Non-Existent Keys)

```java
import com.msp.bloom.BloomFilter;
import com.msp.bloom.Funnels;

// Expecting 1,000,000 keys with a 1% (0.01) false positive rate (~1.1 MB in memory)
BloomFilter<String> cacheFilter = BloomFilter.create(
        Funnels.stringFunnel(),
        1_000_000,
        0.01
);

// Populate during bootstrap
for (String existingUserId : loadAllUserIdsFromDb()) {
    cacheFilter.put(existingUserId);
}

// In request handler:
public User findUser(String userId) {
    // 0% false negative: If mightContain is false, key DEFINITELY does not exist in DB!
    if (!cacheFilter.mightContain(userId)) {
        return null; // Stop immediately, saving expensive DB disk lookup
    }

    // Key probably exists, query cache / database
    return userRepository.findById(userId);
}
```

### 2. Custom Domain Object Funnel

```java
record Transaction(String transactionId, long accountId) {}

Funnel<Transaction> txFunnel = (tx, sink) -> {
    sink.putString(tx.transactionId())
        .putLong(tx.accountId());
};

BloomFilter<Transaction> seenTransactions = BloomFilter.create(txFunnel, 50_000, 0.001);

Transaction tx = new Transaction("tx-98231", 100234L);
seenTransactions.put(tx);

if (seenTransactions.mightContain(tx)) {
    System.out.println("Transaction previously encountered or duplicate candidate!");
}
```

### 3. Inspection & Empirical Statistics

```java
System.out.println("Allocated Bits: " + cacheFilter.bitCount());
System.out.println("Bits Set to 1:  " + cacheFilter.bitsSetCount());
System.out.println("Hash Count (k): " + cacheFilter.hashCount());
System.out.printf("Current Empirical False Positive Rate: %.4f%%%n",
        cacheFilter.falsePositiveProbability() * 100);
```
