# MurmurHash3 Lite (`skill-hashing-murmur3-lite`)

Ultra-fast, zero-dependency implementation of Austin Appleby's **MurmurHash3** algorithm (both 32-bit x86 and 128-bit x64 variants) in idiomatic Java 21 without Google Guava.

## Features
- **Zero Runtime Dependencies**: Completely standalone pure Java 21 standard library.
- **Dual Murmur3 Algorithms**:
  - `x86_32`: 32-bit fast integer hash.
  - `x64_128`: 128-bit hash represented as an immutable `Murmur128Result` record with `toHexString()` and byte conversions.
- **Slice & Seed Support**: Hash any byte array slice `[offset, length]` with custom seed integer.
- **High Performance & Low Collision**: Excellent avalanche properties and low collision rate ideal for bloom filters, hash rings, cache partitioning, and deduplication.

## Usage Examples

### 1. 32-bit MurmurHash3
```java
import com.msp.murmur3.Murmur3;

int hash = Murmur3.hash32("user:session:98472");
int seeded = Murmur3.hash32("user:session:98472", 42);

byte[] rawBytes = ...;
int sliceHash = Murmur3.hash32(rawBytes, 0, rawBytes.length, 0);
```

### 2. 128-bit MurmurHash3
```java
import com.msp.murmur3.Murmur3;
import com.msp.murmur3.Murmur128Result;

Murmur128Result result = Murmur3.hash128("large-dataset-item-identifier");

long h1 = result.val1();
long h2 = result.val2();
String hex32 = result.toHexString(); // 32-char lowercase hex
byte[] raw16 = result.toByteArray(); // 16-byte raw array
```
