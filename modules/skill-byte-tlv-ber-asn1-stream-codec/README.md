# skill-byte-tlv-ber-asn1-stream-codec

Zero-dependency Java 21 streaming ASN.1 BER-TLV and EMV TLV encoder/decoder.

## Architecture Highlights
- **Full BER-TLV Tag Support (`TlvTag`, `TagClass`)**: Single-byte tags (`0..30`) and multi-byte high-tag-number encodings (`>= 31`) across `UNIVERSAL`, `APPLICATION`, `CONTEXT_SPECIFIC`, and `PRIVATE` classes with Primitive/Constructed bit handling.
- **Definite & Indefinite Length Forms (`BerTlvCodec`)**: Supports short-form (`0..127`), long-form (`0x81..0x84`), and indefinite-length (`0x80` ... EOC `0x00 0x00`) streaming decoding with configurable `maxDepth` and `maxValueLength` DoS guards.
- **Sealed Tree Model & Visitor (`TlvNode`, `PrimitiveTlv`, `ConstructedTlv`, `TlvBuilder`, `TlvVisitor`)**: Idiomatic Java 21 sealed hierarchy with fluent construction, recursive search, visitor traversal, and indented hex dump formatting (`TlvHexFormatter`).

## Quick Start

```java
import com.msp.tlvcodec.*;

public class Example {
    public static void main(String[] args) {
        BerTlvCodec codec = new BerTlvCodec();

        ConstructedTlv fci = TlvBuilder.constructedHex("6F")
                .primitiveHex("84", "A0000000031010")
                .nestedHex("A5", a5 -> a5
                        .primitiveUtf8(TlvTag.fromHex("50"), "VISA CREDIT")
                        .primitiveHex("9F26", "0102030405060708"))
                .build();

        byte[] wire = codec.encode(fci);
        TlvNode decoded = codec.decode(wire);
        System.out.println(TlvHexFormatter.formatDump(decoded));
    }
}
```
