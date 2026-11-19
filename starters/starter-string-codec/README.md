# 🔤 starter-string-codec

> **Multi-Pattern String Search, Suffix Arrays, VarInt/LEB128, z-base-32, and ASN.1 BER-TLV Codec Starter Bundle**

Aggregates 6 zero-dependency Java 21 modules:
- `skill-string-ac-automaton-multi-pattern` — O(N + M) Aho-Corasick multi-pattern Trie search automaton
- `skill-string-knuth-morris-pratt-automaton` — O(N) zero-backtracking KMP search (in-memory & streaming)
- `skill-string-suffix-array-lcp-builder` — O(N log N) Suffix Array & Kasai LCP engine (LCS, LRS, substring counts)
- `skill-byte-varint-leb128-protobuf-codec` — Protocol Buffers VarInt, ZigZag, and Signed LEB128 codec
- `skill-byte-zbase32-human-oriented-codec` — Phil Zimmermann's human-oriented `z-base-32` codec
- `skill-byte-tlv-ber-asn1-stream-codec` — Streaming ASN.1 BER-TLV / EMV TLV tree builder, encoder & decoder

## Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:starter-string-codec:2.0.0'
}
```

Or via JitPack:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.rlaguswls13.multi-skill-plugin:starter-string-codec:2.0.0'
}
```

## Quick Start

```java
import com.msp.starter.stringcodec.StringCodecKit;
import com.msp.tlvcodec.TlvTag;
import java.nio.charset.StandardCharsets;
import java.util.List;

// 1. Aho-Corasick multi-pattern search
var matches = StringCodecKit.findAllPatterns("ushers", List.of("he", "she", "his", "hers"));

// 2. KMP single-pattern search
int idx = StringCodecKit.kmpIndexOf("ababcababd", "abc"); // 2

// 3. Suffix Array & LCP
String lcs = StringCodecKit.longestCommonSubstring("xabcdey", "zbcdew"); // "bcde"
String lrs = StringCodecKit.longestRepeatedSubstring("banana");          // "ana"

// 4. VarInt / ZigZag / Signed LEB128
byte[] varint = StringCodecKit.encodeUnsignedVarInt(300L);
long decoded  = StringCodecKit.decodeUnsignedVarInt(varint);

// 5. z-base-32
String token = StringCodecKit.encodeZBase32("Hello MSP!".getBytes(StandardCharsets.UTF_8));
byte[] raw   = StringCodecKit.decodeZBase32(token);

// 6. ASN.1 BER-TLV / EMV TLV
var tlv = StringCodecKit.tlvBuilderHex("6F")
        .primitiveHex("84", "A0000000031010")
        .nestedHex("A5", sub -> sub.primitiveUtf8(TlvTag.fromHex("50"), "VISA"))
        .build();
byte[] encodedTlv = StringCodecKit.encodeBerTlv(tlv);
var decodedTlv    = StringCodecKit.decodeBerTlv(encodedTlv);
```
