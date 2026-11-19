# skill-byte-cbor-streaming-tokenizer

RFC 8949 Concise Binary Object Representation (CBOR) Streaming Tokenizer & Chunk Parser in pure Java 21.

## 🚀 Features

- **Full RFC 8949 Spec Compliance**:
  - Decodes all CBOR Major types 0~7:
    - Major 0: Unsigned integers (up to uint64 / BigInteger).
    - Major 1: Negative integers.
    - Major 2: Byte strings (definite and indefinite length).
    - Major 3: UTF-8 text strings (definite and indefinite length).
    - Major 4: Arrays (definite and indefinite length).
    - Major 5: Maps (definite and indefinite length).
    - Major 6: Semantic tags.
    - Major 7: Booleans, Null, Undefined, Simple values, Half-float (IEEE 754 16-bit), Float (32-bit), Double (64-bit), and Break code (`0xFF`).
- **Streaming Pull-Parser**: Reads token-by-token via `CborTokenizer` without loading whole payloads into heap memory.
- **RFC 8949 Diagnostic Notation Formatter**: Converts CBOR streams directly into diagnostic text representation (e.g., `{1: "text", 2: [1, 2], 3: true, 4: null}`).
- **Lightweight Encoder**: `CborWriter` provides fluent binary CBOR encoding.
- **Zero Runtime Dependencies**: Written entirely in pure Java 21.

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation project(':modules:skill-byte-cbor-streaming-tokenizer')
}
```

## 💡 Quick Start

### 1. Tokenizing CBOR Stream

```java
import com.msp.cborstream.CborTokenizer;
import com.msp.cborstream.CborToken;
import java.io.InputStream;

public class Example {
    public static void main(String[] args) throws Exception {
        InputStream stream = ...;
        try (CborTokenizer tokenizer = new CborTokenizer(stream)) {
            while (tokenizer.hasNext()) {
                CborToken token = tokenizer.nextToken();
                System.out.println("Token: " + token.type() + ", value: " + token.value());
            }
        }
    }
}
```

### 2. Diagnostic Notation Formatting

```java
import com.msp.cborstream.CborDiagnosticFormatter;

byte[] cborBytes = ...;
String diagnostic = CborDiagnosticFormatter.format(cborBytes);
System.out.println(diagnostic); // e.g. {1: "text", 2: [1, 2], 3: true}
```
