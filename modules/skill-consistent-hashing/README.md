# ⭕ skill-consistent-hashing

> **High-Performance Consistent Hash Ring with Virtual Nodes for Minimal Cluster Rebalancing**  
> Pure Java 21 standard library, thread-safe consistent hashing with configurable 64-bit Murmur3 / FNV-1a algorithms and replica set selection.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 only (`ConcurrentSkipListMap`, `StandardCharsets`).
- **Minimal Rebalancing ($1/N$)**: When cluster membership changes, only $\approx 1/N$ of keys are relocated, avoiding massive cache churn or database stampedes.
- **Virtual Nodes**: Configurable virtual nodes per physical node (default 150) guaranteeing uniform key distribution across servers.
- **Replica Sets (`getNodes(key, count)`)**: Extracts $k$ distinct physical nodes in clockwise ring order for quorum and replication setups without physical duplicate nodes.
- **64-bit MurmurHash3 & FNV-1a**: Built-in high-avalanche 64-bit hashing algorithm.
- **Thread-Safe**: Fully safe for concurrent reads and dynamic cluster scaling.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-consistent-hashing:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Basic Key Routing

```java
import com.msp.hashring.ConsistentHashRing;

// Create ring with default 150 virtual nodes
ConsistentHashRing<String> ring = new ConsistentHashRing<>();

// Register cluster nodes
ring.addNode("redis-node-1:6379");
ring.addNode("redis-node-2:6379");
ring.addNode("redis-node-3:6379");

// Route keys to primary node
String primaryNode = ring.getNodeOrThrow("user:session:10042");
System.out.println("Routed to: " + primaryNode);
```

### 2. Multi-Replica Quorum Selection

```java
import com.msp.hashring.ConsistentHashRing;
import java.util.List;

ConsistentHashRing<String> ring = new ConsistentHashRing<>(200);
ring.addNode("storage-node-A");
ring.addNode("storage-node-B");
ring.addNode("storage-node-C");
ring.addNode("storage-node-D");

// Retrieve 3 unique physical replica nodes for replication
List<String> replicas = ring.getNodes("file:invoice_202610.pdf", 3);
System.out.println("Primary & Secondary Replicas: " + replicas);
```

### 3. Custom Node Types and Hash Functions

```java
import com.msp.hashring.ConsistentHashRing;
import com.msp.hashring.HashFunction;

record ServerNode(String id, String host, int port) {}

ConsistentHashRing<ServerNode> serverRing = new ConsistentHashRing<>(
    150,
    HashFunction.FNV1A_64,
    node -> node.host() + ":" + node.port()
);

serverRing.addNode(new ServerNode("srv-1", "10.0.0.1", 8080));
serverRing.addNode(new ServerNode("srv-2", "10.0.0.2", 8080));

ServerNode target = serverRing.getNodeOrThrow("order:9901");
```
