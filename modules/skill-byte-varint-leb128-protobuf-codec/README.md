# skill-byte-varint-leb128-protobuf-codec

Protocol Buffers / WebAssembly Standard VarInt, Unsigned LEB128, Signed LEB128, and ZigZag Variable-Length Integer Codec in pure Java 21.

## Features
- **Unsigned VarInt / ULEB128**: Encodes 7 bits per byte with the MSB (`0x80`) continuation bit across `byte[]`, `ByteBuffer`, and `InputStream`/`OutputStream`.
- **ZigZag VarInt (`sint64`)**: Uses `(n << 1) ^ (n >> 63)` bijection so negative integers with small magnitude (`-1`, `-2`, ...) compress to 1 byte instead of 10 bytes.
- **Signed LEB128**: Implements DWARF / WebAssembly standard two's complement sign-extended LEB128 (`writeSignedLeb128` / `readSignedLeb128`).
- **O(1) Byte Length Calculation**: `computeUnsignedVarIntSize(long)`, `computeZigZagVarIntSize(long)`, and `computeSignedLeb128Size(long)`.

## Usage Example

```java
package com.msp.varint.example;

import com.msp.varint.VarIntLeb128Codec;
import java.util.Arrays;

public class VarIntExample {
    public static void main(String[] args) {
        // 1. Unsigned VarInt (300 -> [0xAC, 0x02])
        byte[] uvar = VarIntLeb128Codec.writeUnsignedVarInt(300L);
        System.out.println("Unsigned 300 bytes: " + Arrays.toString(uvar));
        System.out.println("Decoded Unsigned: " + VarIntLeb128Codec.readUnsignedVarInt(uvar));

        // 2. ZigZag VarInt (-1 -> [0x01], 1 byte!)
        byte[] zigzag = VarIntLeb128Codec.writeZigZagVarInt(-1L);
        System.out.println("ZigZag -1 byte length: " + zigzag.length);
        System.out.println("Decoded ZigZag: " + VarIntLeb128Codec.readZigZagVarInt(zigzag));

        // 3. Signed LEB128 (-123456 -> [0xC0, 0xBB, 0x78])
        byte[] sleb = VarIntLeb128Codec.writeSignedLeb128(-123456L);
        System.out.println("Decoded Signed LEB128: " + VarIntLeb128Codec.readSignedLeb128(sleb));
    }
}
```
