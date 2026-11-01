# Process Stream Pumper (`skill-process-stream-pumper`)

Asynchronous stream pumper transferring process standard output (`stdout`) and error (`stderr`) streams without heap Out-Of-Memory (OOM) or OS pipe deadlocks using Java 21 Virtual Threads.

## Features
- **Java 21 Virtual Threads**: Non-blocking asynchronous I/O with negligible memory overhead per stream pumping task.
- **Pipe Deadlock Prevention**: Drains process outputs concurrently to avoid OS pipe buffer saturation and sub-process freezing.
- **Multiple Pump Targets**:
  - `pumpAsync(InputStream, OutputStream)`: Stream-to-stream pumping with configurable buffer size.
  - `pumpToFileAsync(InputStream, Path)`: Directly stream process output to disk file without buffering the full content in heap memory.
  - `pumpToLineConsumer(InputStream, Consumer<String>)`: Asynchronously process output line-by-line (e.g. for real-time logging, metrics, or command output parsing).
- **Comprehensive Metrics**: `StreamPumpResult` provides total bytes transferred, duration, status, and error details.
- **Zero Dependencies**: Pure Java 21 standard library.

## Usage

```java
import com.msp.streampump.ProcessStreamPumper;
import com.msp.streampump.StreamPumpResult;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class Example {
    public static void main(String[] args) throws Exception {
        Process process = new ProcessBuilder("ping", "127.0.0.1").start();

        // 1. Line-by-line streaming to logger/console
        CompletableFuture<StreamPumpResult> stdoutFuture =
                ProcessStreamPumper.pumpToLineConsumer(process.getInputStream(), line -> {
                    System.out.println("[STDOUT] " + line);
                });

        // 2. Dump stderr directly to file
        CompletableFuture<StreamPumpResult> stderrFuture =
                ProcessStreamPumper.pumpToFileAsync(process.getErrorStream(), Path.of("process_err.log"));

        // Wait for both pumps to finish
        CompletableFuture.allOf(stdoutFuture, stderrFuture).join();
        process.waitFor();

        StreamPumpResult outResult = stdoutFuture.get();
        System.out.println("Bytes pumped: " + outResult.bytesPumped() + " in " + outResult.duration().toMillis() + "ms");
    }
}
```
