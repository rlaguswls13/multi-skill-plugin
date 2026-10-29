# ⚖️ skill-round-robin-balancer

> **Weighted Round-Robin and Smooth Weighted Round-Robin load balancer**  
> Pure Java 21 zero-dependency library implementing standard Round-Robin, Classic WRR (LVS/IPVS GCD), and Nginx-style Smooth Weighted Round-Robin (SWRR) algorithms with dynamic node updates and full thread safety.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Unified Interface**: Clean, generic `LoadBalancer<T>` abstraction with `next()` and non-blocking `tryNext()`.
- **Three Balancing Algorithms**:
  1. `RoundRobinBalancer<T>`: Atomic cyclic index with lock-free modulo rotation.
  2. `WeightedRoundRobinBalancer<T>`: Classical GCD-based weighted round-robin.
  3. `SmoothWeightedRoundRobinBalancer<T>`: Nginx upstream algorithm that completely eliminates burst clustering.
- **Smooth Interleaving (No High-Weight Bursts)**:
  - If server weights are `A: 5, B: 1, C: 1`, classical WRR produces `[A, A, A, A, A, B, C]` (server A is hammered 5 times continuously).
  - SWRR smoothly distributes requests as `[A, A, B, A, C, A, A]`.
- **Dynamic Membership**: Add and remove servers on the fly without service disruption.
- **Thread-Safe**: Safe for concurrent high-throughput dispatching across virtual and platform threads.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-round-robin-balancer:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Nginx-Style Smooth Weighted Balancing

```java
import com.msp.balancer.SmoothWeightedRoundRobinBalancer;

// Configure backend servers with capacity weights
SmoothWeightedRoundRobinBalancer<String> balancer = new SmoothWeightedRoundRobinBalancer<>();
balancer.addNode("https://api-large-1.local", 5); // 5x capacity
balancer.addNode("https://api-small-1.local", 1); // 1x capacity
balancer.addNode("https://api-small-2.local", 1); // 1x capacity

// Dispatch requests smoothly (no consecutive 5-request burst to large-1)
for (int i = 0; i < 7; i++) {
    String server = balancer.next();
    System.out.println("Routed request to: " + server);
}
// Outputs: large-1, large-1, small-1, large-1, small-2, large-1, large-1
```

### 2. Standard Atomic Round-Robin

```java
import com.msp.balancer.RoundRobinBalancer;
import java.util.List;

RoundRobinBalancer<String> rr = new RoundRobinBalancer<>(
        List.of("db-replica-1", "db-replica-2", "db-replica-3")
);

String replica = rr.next();
System.out.println("Reading from: " + replica);

// Dynamic maintenance: mark replica-2 offline
rr.removeNode("db-replica-2");
```

### 3. Graceful Polling with `tryNext()`

```java
import com.msp.balancer.RoundRobinBalancer;
import java.util.Optional;

RoundRobinBalancer<String> balancer = new RoundRobinBalancer<>();

Optional<String> target = balancer.tryNext();
if (target.isEmpty()) {
    System.err.println("No backend servers available!");
} else {
    sendRequest(target.get());
}
```
