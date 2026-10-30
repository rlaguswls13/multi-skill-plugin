# ⚡ Precision Circuit Breaker State Machine (`skill-circuit-breaker-state-machine`)

> Zero-dependency, thread-safe, high-performance Java 21 implementation of the **Circuit Breaker** stability pattern.

---

## 🌟 Key Features

1. **Precision 3-State Model**:
   - `CLOSED`: Normal operation, sliding window monitors call failure rates.
   - `OPEN`: Downstream protection, rejects calls immediately.
   - `HALF_OPEN`: Probe state allowing a configurable number of trial calls to test service recovery.
2. **Sliding Window Metrics**:
   - Circular buffer window tracking exact failure percentages.
   - Configurable `minimumNumberOfCalls` before tripping.
3. **Thread Safety & Zero Runtime Dependencies**:
   - Guarded by lightweight `ReentrantLock` ensuring atomic transitions under heavy concurrent load.
   - 100% pure Java 21 standard library.
4. **Convenient Functional Execution**:
   - `executeSupplier(Supplier<T>)` and `executeRunnable(Runnable)` with automatic metric collection.

---

## 🚀 Quick Start

```java
import com.msp.circuit.CircuitBreakerConfig;
import com.msp.circuit.CircuitBreakerStateMachine;
import com.msp.circuit.CircuitBreakerOpenException;
import java.time.Duration;

// 1. Configure the Circuit Breaker
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .failureRateThreshold(50.0f)                       // Trip if >= 50% fail
        .slidingWindowSize(10)                             // Monitor last 10 calls
        .minimumNumberOfCalls(5)                           // Minimum calls before evaluation
        .waitDurationInOpen(Duration.ofSeconds(10))        // Wait 10s before probe
        .permittedNumberOfCallsInHalfOpenState(3)          // 3 probe calls
        .build();

CircuitBreakerStateMachine circuitBreaker = new CircuitBreakerStateMachine(config);

// 2. Functional execution wrapper
try {
    String response = circuitBreaker.executeSupplier(() -> callExternalService());
} catch (CircuitBreakerOpenException e) {
    // Fast fallback when downstream is degraded
    System.out.println("Circuit is OPEN, returning cached fallback");
}

// 3. Manual permission checking
if (circuitBreaker.tryAcquirePermission()) {
    try {
        doSomething();
        circuitBreaker.recordSuccess();
    } catch (Exception e) {
        circuitBreaker.recordFailure();
    }
}
```
