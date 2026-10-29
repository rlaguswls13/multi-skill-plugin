# 📊 skill-hyper-log-log-lite

> **Ultra-Memory-Efficient HyperLogLog (HLL) Cardinality Estimator with 64-Bit Hashing & Bias Correction**  
> Pure Java 21 standard library, configurable precision ($b \in [4, 16]$), LinearCounting small cardinality correction, and algebraic merge operations.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 only.
- **Tiny Memory Footprint**: Precision $b = 12$ requires only $4\,\text{KB}$ of RAM ($4096$ registers) while tracking billions of unique visitors with $\approx 1.625\%$ standard error.
- **Small Cardinality Bias Correction**: Uses Linear Counting for $E \le 2.5m$ so small sets (1 to 100 items) are counted near-exactly.
- **Set Union / Merge**: Highly efficient register-wise maximum merging ($\max(A_i, B_i)$) for distributed map-reduce cardinality aggregation.
- **Multi-Type Ingestion**: Native support for `String`, `long`, and raw `byte[]`.
- **64-bit MurmurHash3**: Avalanche-tested 64-bit hashing ensuring uniform register distribution.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-hyper-log-log-lite:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Unique Visitor Counting

```java
import com.msp.hll.HyperLogLog;

// Initialize HLL with precision 12 (4096 registers, ~1.625% error, 4KB RAM)
HyperLogLog uniqueVisitors = new HyperLogLog(12);

// Ingest user identifiers
uniqueVisitors.add("user_1001");
uniqueVisitors.add("user_1002");
uniqueVisitors.add("user_1001"); // Duplicate - does not inflate count

System.out.println("Estimated Unique Visitors: " + uniqueVisitors.cardinality()); // 2
```

### 2. Distributed Aggregation via Merge

```java
import com.msp.hll.HyperLogLog;

// Estimator on Server 1
HyperLogLog server1Hll = new HyperLogLog(12);
server1Hll.add("ip_192.168.1.1");
server1Hll.add("ip_192.168.1.2");

// Estimator on Server 2
HyperLogLog server2Hll = new HyperLogLog(12);
server2Hll.add("ip_192.168.1.2"); // Overlapping IP
server2Hll.add("ip_192.168.1.3");

// Merge results into unified metric
HyperLogLog aggregated = HyperLogLog.merge(server1Hll, server2Hll);
System.out.println("Total Unique IPs Across Cluster: " + aggregated.cardinality()); // 3
```
