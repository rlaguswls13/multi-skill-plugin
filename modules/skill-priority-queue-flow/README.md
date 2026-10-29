# 🚦 skill-priority-queue-flow

> **Priority-Based (P0–P3) In-Memory Work Queue & Async Worker Pool for Java 21**  
> Supports fair deficit-weighted scheduling to prevent starvation of low-priority tasks while processing critical and high-priority workloads first.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library (`ReentrantLock`, `Condition`, `Thread.ofVirtual()`).
- **4-Tier Priority Levels**:
  - `Priority.CRITICAL` (P0) — default weight: 8
  - `Priority.HIGH` (P1) — default weight: 4
  - `Priority.NORMAL` (P2) — default weight: 2
  - `Priority.LOW` (P3) — default weight: 1
- **Anti-Starvation Fair Scheduling**:
  - Uses Deficit Weighted Quotas so lower priority items are guaranteed service within every quota window.
  - Can switch to strict priority drain (`setFairSchedulingEnabled(false)`) if needed.
- **Asynchronous `WorkerPool`**:
  - Backed by Java 21 Virtual Threads (or platform threads).
  - Built-in pause/resume, error callbacks, and graceful termination.
  - Implements `AutoCloseable`.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-priority-queue-flow:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Priority Work Queue with Fair Scheduling

```java
import com.msp.pqueue.PriorityWorkQueue;
import com.msp.pqueue.Priority;

PriorityWorkQueue<String> queue = new PriorityWorkQueue<>();

// Enqueue mixed priorities
queue.submit("Background-Report", Priority.LOW);
queue.submit("Email-Notification", Priority.NORMAL);
queue.submit("Order-Payment", Priority.HIGH);
queue.submit("System-Security-Alert", Priority.CRITICAL);

// Higher priority items drain first, while preventing low-priority starvation
String nextItem = queue.take(); // "System-Security-Alert"
```

### 2. High-Throughput Worker Pool (Virtual Threads)

```java
import com.msp.pqueue.PriorityWorkQueue;
import com.msp.pqueue.WorkerPool;
import com.msp.pqueue.Priority;

PriorityWorkQueue<TaskCommand> queue = new PriorityWorkQueue<>();

try (WorkerPool<TaskCommand> pool = WorkerPool.builder(queue, task -> {
            task.execute();
        })
        .workers(16)
        .useVirtualThreads(true)
        .onError((task, ex) -> System.err.println("Task failed: " + task + ", err: " + ex))
        .build()) {

    pool.start();

    // Submit workloads
    queue.submit(new TaskCommand("P0-Task"), Priority.CRITICAL);
    queue.submit(new TaskCommand("P2-Task"), Priority.NORMAL);

    // Dynamic control
    pool.pause();
    // ... do maintenance ...
    pool.resume();
}
// Pool automatically stops and awaits graceful termination on close
```
