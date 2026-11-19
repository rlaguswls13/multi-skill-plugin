# BitTorrent Bencode Torrent Codec (`skill-byte-bencode-torrent-codec`)

Pure Java 21 bidirectional serialization and deserialization codec for the BitTorrent Bencode format (BEP 0003).

## Features

- **Full BEP 0003 Compliance**:
  - Integers (`i<number>e`): handles arbitrary 64-bit numbers; strictly forbids negative zeroes (`i-0e`) and leading zeroes (`i03e`).
  - Byte Strings (`<len>:<content>`): handles arbitrary binary payloads and UTF-8 strings.
  - Lists (`l<contents>e`): arbitrary nesting of values.
  - Dictionaries (`d<key><val>...e`): keys are strictly validated and sorted in lexicographical unsigned byte order.
- **Idiomatic Java 21 Data Model**:
  - Sealed hierarchy: `BencodeValue` permits `BencodeInteger`, `BencodeString`, `BencodeList`, `BencodeDict`.
  - Convenient dictionary accessors (`getInt`, `getString`, `getBytes`, `getList`, `getDict`).
  - Fluent `BencodeDict.builder()`.
- **Java Object Serialization**: Automatically encodes Java `Map`, `List`, `String`, `byte[]`, and numbers into valid Bencode.
- **Zero Runtime Dependencies**: Pure Java 21.

## Usage Example

```java
import com.msp.bencode.*;

// 1. Decode a .torrent file or bencode byte array
byte[] data = "d4:spaml1:a1:bee".getBytes(StandardCharsets.US_ASCII);
BencodeDict dict = Bencode.decodeDict(data);

BencodeList list = dict.getList("spam").orElseThrow();
System.out.println("Size: " + list.size()); // 2

// 2. Build and encode a Torrent metainfo dictionary
BencodeDict torrent = BencodeDict.builder()
    .put("announce", "http://tracker.example.com/announce")
    .put("creation date", System.currentTimeMillis() / 1000L)
    .put("info", BencodeDict.builder()
        .put("name", "ubuntu-24.04.iso")
        .put("piece length", 262144L)
        .put("length", 4294967296L)
        .put("pieces", sha1HashesByteArray)
        .build())
    .build();

byte[] bencoded = Bencode.encode(torrent);
```
