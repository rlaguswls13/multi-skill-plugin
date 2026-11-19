# Concurrent Event Ring Sequencer (`skill-concurrent-event-ring-sequencer`)

LMAX Disruptor-style Single-Producer/Multi-Consumer Lock-Free Ring Sequencer Event Queue for Java 21.

## Architecture

- **Power-of-Two Ring Buffer**: Uses bitwise mask (`index = sequence & (bufferSize - 1)`) for $O(1)$ slot lookup without expensive modulo operations.
- **Cache-Line Padded Sequences**: `Sequence` provides 64-bit atomic cursors with 56 bytes of padding on either side to eliminate cache line ping-pong (false sharing) on multi-core processors.
- **Gating Sequences**: Prevents the producer from wrapping around and overwriting events before downstream consumers have processed them.
- **Wait Strategies**:
  - `BusySpinWaitStrategy`: Lowest latency via `Thread.onSpinWait()`, high CPU usage.
  - `YieldingWaitStrategy`: Hybrid spinning then cooperative `Thread.yield()`.
  - `BlockingWaitStrategy`: Conservative lock/condition signaling, 0% CPU when idle.
  - `SleepingWaitStrategy`: Multi-tier spinning, yielding, and nanosecond parking.
- **Zero-Allocation Ingestion**: Pre-allocates event instances at startup. Publishing modifies events in-place via `EventTranslator` without runtime garbage collection pressure.

## Usage Example

```java
import com.msp.sequencer.*;

// 1. Define event class
class OrderEvent {
    long orderId;
    double price;
}

// 2. Create RingBuffer (size must be power of 2)
RingBuffer<OrderEvent> ringBuffer = RingBuffer.createSingleProducer(
    OrderEvent::new,
    1024,
    new YieldingWaitStrategy()
);

// 3. Create Consumer (BatchEventProcessor)
SequenceBarrier barrier = ringBuffer.newBarrier();
BatchEventProcessor<OrderEvent> processor = new BatchEventProcessor<>(
    ringBuffer,
    barrier,
    (event, sequence, endOfBatch) -> {
        System.out.println("Processing order " + event.orderId + " at seq " + sequence);
    }
);

// 4. Register gating sequence & start consumer thread
ringBuffer.addGatingSequence(processor.getSequence());
Thread.ofPlatform().start(processor);

// 5. Publish events zero-allocation
ringBuffer.publishEvent((event, seq) -> {
    event.orderId = 12345L;
    event.price = 99.95;
});

// Shutdown when done
processor.halt();
```
