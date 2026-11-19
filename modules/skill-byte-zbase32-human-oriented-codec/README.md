# skill-byte-zbase32-human-oriented-codec

Zero-dependency Java 21 implementation of Phil Zimmermann's human-oriented **z-base-32** (`ybndrfg8ejkmcpqxot1uwisza345h769`) encoder/decoder.

## Architecture Highlights
- **Human-Oriented Alphabet**: Uses `ybndrfg8ejkmcpqxot1uwisza345h769`, omitting visually ambiguous characters (`0`, `2`, `l`, `v`) with no `=` padding.
- **Byte & Arbitrary Bit-Length Support**: Supports full byte arrays (`encode`/`decode`) as well as exact arbitrary bit counts (`encodeBits`/`decodeBits`).
- **Case-Insensitive & Strict Validation**: Decodes upper/lower case seamlessly; `ZBase32Codec.strict()` rejects invalid characters, illegal byte-aligned lengths, and non-zero trailing padding bits (with `ZBase32Codec.lenient()` available when needed).

## Usage Example
```java
import com.msp.zbase32.ZBase32Codec;

byte[] raw = new byte[]{(byte) 0xd4, 0x7a, 0x04};
String encoded = ZBase32Codec.encodeBytes(raw);      // "4t7ye"
byte[] decoded = ZBase32Codec.decodeBytes("4T7YE");  // {0xd4, 0x7a, 0x04}

// Arbitrary bit-length encoding (e.g., 10-bit token)
String bits10 = ZBase32Codec.strict().encodeBits(new byte[]{(byte) 0xff, (byte) 0xc0}, 10); // "99"
```
