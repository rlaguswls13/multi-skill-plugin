# Process Standard Stream Multiplexer (`skill-process-std-stream-multiplexer`)

Thread-safe Multi-Process Standard Stream Multiplexer combining stdout and stderr streams of $N$ processes into a single unified stream using Java 21 Virtual Threads.

## Features

- **Concurrent Stream Ingestion**: Spawns lightweight Virtual Threads per process stream without blocking platform threads.
- **Atomic Line Buffering**: Prevents interleaving of characters mid-sentence; guarantees atomic whole-line emission.
- **Contextual Tagging**:
  - `processTag`: Identifier for process (e.g. `[worker-1]`, `[db-migration]`).
  - `streamType`: `STDOUT` or `STDERR`.
  - `timestamp`: ISO-8601 or relative elapsed time (`+125ms`).
  - `sequence`: Globally monotonic line sequence counter.
- **Flexible Sinks**:
  - Functional `Consumer<StreamLine>`
  - `OutputStream` / `PrintStream` with customizable line formatters
  - Thread-safe `List<StreamLine>`
- **Pure Java 21**: Zero runtime dependencies.

## Usage Example

```java
import com.msp.streammux.*;
import java.time.Duration;

// 1. Launch external child processes
Process worker1 = new ProcessBuilder("worker-cli", "--id", "1").start();
Process worker2 = new ProcessBuilder("worker-cli", "--id", "2").start();

// 2. Multiplex stdout and stderr to System.out
try (ProcessStreamMultiplexer mux = ProcessStreamMultiplexer.builder()
        .sink(StreamSink.toPrintStream(System.out, StreamLine::formatted))
        .attach("worker-1", worker1)
        .attach("worker-2", worker2)
        .build()) {

    mux.start();

    // 3. Await completion of all process output streams
    mux.awaitCompletion(Duration.ofMinutes(5));
}
```
