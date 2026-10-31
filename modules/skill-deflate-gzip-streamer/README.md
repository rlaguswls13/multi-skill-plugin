# GZIP & DEFLATE Streamer (`skill-deflate-gzip-streamer`)

High-throughput, zero-dependency streaming and byte-array compression pipeline for Java 21 standard library.

## Features
- **Zero Dependencies**: Pure Java 21 (`java.util.zip`) with no external runtime jars.
- **Dual Engine**: Seamless support for both **GZIP** and **DEFLATE** compression algorithms.
- **Streaming Pipeline**: Efficient buffer handling (`8KB` chunks) for large streams without loading entire contents into heap memory.
- **Safe Magic Inspection**: Non-destructive GZIP magic number checking (`0x1F, 0x8B`) for byte arrays and markable input streams.
- **Thread-Safe**: Fully stateless utility methods ready for concurrent execution.

## Usage Examples

### 1. In-Memory GZIP & DEFLATE Compression
```java
import com.msp.gzipstream.GzipStreamer;
import java.nio.charset.StandardCharsets;

byte[] raw = "Sample high volume payload for compression".getBytes(StandardCharsets.UTF_8);

// GZIP
byte[] gzipData = GzipStreamer.compressGzip(raw);
byte[] restoredGzip = GzipStreamer.decompressGzip(gzipData);

// Magic Number Check
boolean isGzip = GzipStreamer.isGzip(gzipData); // true

// DEFLATE (Level 0 - 9)
byte[] deflated = GzipStreamer.compressDeflate(raw, 9);
byte[] restoredDeflate = GzipStreamer.decompressDeflate(deflated);
```

### 2. Large File / Stream Compression Pipeline
```java
import com.msp.gzipstream.GzipStreamer;
import com.msp.gzipstream.CompressionType;
import java.io.InputStream;
import java.io.OutputStream;

try (InputStream in = ...; OutputStream out = ...) {
    // Compress directly from input to output stream
    long bytesRead = GzipStreamer.compressStream(in, out, CompressionType.GZIP);

    // Decompress directly from input to output stream
    long bytesWritten = GzipStreamer.decompressStream(in, out, CompressionType.DEFLATE);
}
```
