# skill-byte-base91-basE91-codec

> Standard basE91 Binary-to-ASCII bidirectional codec with streaming support.

---

## 🚀 Features

- **High-Density Binary-to-ASCII Encoding**:
  - Uses 91 printable ASCII characters (table size 91):
    `ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$%&()*+,./:;<=>?@[]^_`{|}~"`
  - Encodes 13 or 14 bits into 2 characters, yielding an average expansion ratio of **~1.23x**, significantly smaller and more compact than Base64's **~1.33x** (saving ~8% bandwidth and storage).
- **Comprehensive API**:
  - `Base91.encode(byte[] data)` -> `String`
  - `Base91.encodeToBytes(byte[] data)` -> `byte[]`
  - `Base91.decode(String encoded)` -> `byte[]`
  - `Base91.decode(byte[] encoded)` -> `byte[]`
  - Streaming support: `Base91InputStream`, `Base91OutputStream`, `Base91.wrap(in)`, `Base91.wrap(out)`.
- **Whitespace Tolerant**:
  - The decoder ignores standard ASCII formatting whitespace (`\r`, `\n`, spaces, tabs) for easy transmission across text protocols.
- **Pure Java 21**: Zero runtime dependencies.

---

## 📦 Usage Examples

### 1. Basic In-Memory Encode & Decode

```java
import com.msp.base91.Base91;
import java.nio.charset.StandardCharsets;

byte[] secretData = "High density data compression with basE91".getBytes(StandardCharsets.UTF_8);

// Encode to basE91 String
String encoded = Base91.encode(secretData);
System.out.println("Encoded (" + encoded.length() + " chars): " + encoded);

// Decode back
byte[] decoded = Base91.decode(encoded);
System.out.println("Decoded: " + new String(decoded, StandardCharsets.UTF_8));
```

### 2. Streaming via OutputStream & InputStream

```java
import com.msp.base91.Base91OutputStream;
import com.msp.base91.Base91InputStream;
import java.io.*;

// Streaming output: writes basE91 characters directly
ByteArrayOutputStream out = new ByteArrayOutputStream();
try (Base91OutputStream b91Out = new Base91OutputStream(out)) {
    b91Out.write(rawBytes);
}

// Streaming input: reads basE91 characters and yields raw bytes
try (Base91InputStream b91In = new Base91InputStream(new ByteArrayInputStream(out.toByteArray()))) {
    byte[] restored = b91In.readAllBytes();
}
```
