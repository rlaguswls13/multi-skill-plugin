# HTTP/2 HPACK Header Compression Lite (`skill-http-hpack-header-compression-lite`)

Zero-dependency, pure Java 21 implementation of RFC 7541 (HPACK: Header Compression for HTTP/2). Provides complete static table lookup, FIFO dynamic table management, integer variable-length prefix coding, and static Huffman coding.

## Features

- **RFC 7541 Static Table**: Pre-defined 61 header fields (indices 1 to 61) with $O(1)$ fast lookups for both exact (name + value) matches and name matches.
- **Dynamic Table**: Configurable maximum capacity (default 4096 octets), automatic FIFO eviction when size exceeds limit, and dynamic table size updates (`001xxxxx`).
- **Static Huffman Coding**: Full implementation of RFC 7541 Appendix B 257-symbol Huffman tree, supporting canonical prefix decoding and padding verification.
- **Sensitive Header Protection**: Supports Literal Header Field Never Indexed (`0001xxxx`) for sensitive headers such as tokens or cookies.
- **Pure Java 21**: Zero runtime dependencies, thread-safe dynamic table synchronization, and high performance.

## Runnable Code Example

```java
import com.msp.hpack.HeaderField;
import com.msp.hpack.HpackDecoder;
import com.msp.hpack.HpackEncoder;

import java.util.List;

public class HpackExample {
    public static void main(String[] args) {
        // 1. Initialize encoder and decoder with shared or default dynamic table capacity (4096 bytes)
        HpackEncoder encoder = new HpackEncoder(4096, true);
        HpackDecoder decoder = new HpackDecoder(4096);

        // 2. Define headers
        List<HeaderField> requestHeaders = List.of(
                new HeaderField(":method", "GET"),
                new HeaderField(":scheme", "https"),
                new HeaderField(":path", "/api/v1/orders"),
                new HeaderField(":authority", "example.com"),
                new HeaderField("custom-trace-id", "req-abcdef-123456"),
                new HeaderField("authorization", "Bearer eyJhbGciOi...", true) // sensitive
        );

        // 3. Compress header block
        byte[] compressedBlock = encoder.encode(requestHeaders);
        System.out.println("Compressed byte size: " + compressedBlock.length);

        // 4. Decompress header block
        List<HeaderField> decodedHeaders = decoder.decode(compressedBlock);
        for (HeaderField header : decodedHeaders) {
            System.out.println(header.name() + ": " + header.value());
        }

        // 5. Subsequent request with identical headers benefits from dynamic table indexing
        byte[] secondBlock = encoder.encode(requestHeaders);
        System.out.println("Second request compressed size: " + secondBlock.length);
    }
}
```
