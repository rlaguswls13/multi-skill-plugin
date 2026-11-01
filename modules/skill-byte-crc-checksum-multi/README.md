# Multi-Standard Byte CRC Checksum (`skill-byte-crc-checksum-multi`)

Pure Java, table-accelerated bitwise CRC calculator supporting standard 16-bit and 32-bit CRC algorithms without any 3rd-party dependencies.

## Supported CRC Standards
- **CRC-16/MODBUS**: Modbus protocol checksum (Poly 0x8005, reversed 0xA001, Init 0xFFFF). Test vector `"123456789"` = `0x4B37`.
- **CRC-16/CCITT**: ITU-T V.41 / X.25 standard (Poly 0x1021, Init 0xFFFF). Test vector `"123456789"` = `0x29B1`.
- **CRC-16/XMODEM**: XMODEM protocol (Poly 0x1021, Init 0x0000). Test vector `"123456789"` = `0x31C3`.
- **CRC-32/IEEE 802.3**: Standard Ethernet, PNG, ZIP, gzip (Poly 0x04C11DB7, Init 0xFFFFFFFF). Fully compatible with `java.util.zip.CRC32`. Test vector `"123456789"` = `0xCBF43926`.
- **CRC-32C/Castagnoli**: iSCSI, SCTP, Btrfs, Kafka hardware-accelerated CRC-32C (Poly 0x1EDC6F41, Init 0xFFFFFFFF). Test vector `"123456789"` = `0xE3069283`.

## Features
- **Zero Dependencies**: Pure Java 21 implementation.
- **Lookup-Table Accelerated**: Precomputed 256-entry lookup tables for lightning-fast bitwise processing.
- **Dual Computation Modes**:
  - One-shot static methods (`compute16`, `compute32`).
  - Streaming incremental calculator interface (`CrcChecksum`).
- **Hex Formatting**: Built-in 4-digit and 8-digit uppercase hex formatters.

## Usage

```java
import com.msp.checksum.CrcAlgorithm;
import com.msp.checksum.CrcChecksum;
import com.msp.checksum.MultiCrcCalculator;
import java.nio.charset.StandardCharsets;

public class Example {
    public static void main(String[] args) {
        byte[] payload = "Hello World".getBytes(StandardCharsets.UTF_8);

        // 1. One-shot calculation
        int modbus = MultiCrcCalculator.compute16(CrcAlgorithm.CRC16_MODBUS, payload);
        System.out.println("Modbus: 0x" + MultiCrcCalculator.toHexString16(modbus));

        long castagnoli = MultiCrcCalculator.compute32(CrcAlgorithm.CRC32_CASTAGNOLI, payload);
        System.out.println("CRC-32C: 0x" + MultiCrcCalculator.toHexString32(castagnoli));

        // 2. Incremental streaming
        CrcChecksum stream = MultiCrcCalculator.create(CrcAlgorithm.CRC32_IEEE);
        stream.update(payload, 0, 5);
        stream.update(payload, 5, payload.length - 5);
        System.out.println("Streaming CRC32: " + stream.toHexString());
    }
}
```
