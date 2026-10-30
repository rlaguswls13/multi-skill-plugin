package com.msp.hexdump;

import java.util.Objects;

/**
 * 네트워크 패킷, 파일 바이너리 디버깅을 위한 Wireshark/xxd 스타일 Hex Dump 포맷터.
 * (오프셋 주소 + 16진 바이트 뷰 + ASCII 인쇄 문자 뷰)
 */
public final class HexDumpFormatter {

    private HexDumpFormatter() {}

    public static String format(byte[] data) {
        return format(data, HexDumpOptions.standard());
    }

    public static String format(byte[] data, HexDumpOptions options) {
        if (data == null || data.length == 0) {
            return "";
        }
        Objects.requireNonNull(options, "options must not be null");

        int bpl = Math.max(1, options.bytesPerLine());
        int half = bpl / 2;
        StringBuilder sb = new StringBuilder();

        for (int offset = 0; offset < data.length; offset += bpl) {
            // 1. Offset
            sb.append(String.format("%08X  ", offset));

            // 2. Hex Bytes
            int lineLen = Math.min(bpl, data.length - offset);
            for (int i = 0; i < bpl; i++) {
                if (i < lineLen) {
                    byte b = data[offset + i];
                    sb.append(options.uppercase() ? String.format("%02X ", b) : String.format("%02x ", b));
                } else {
                    sb.append("   "); // padding
                }
                if (options.groupHalf() && i == half - 1) {
                    sb.append(" ");
                }
            }

            // 3. ASCII View
            if (options.showAscii()) {
                sb.append(" |");
                for (int i = 0; i < lineLen; i++) {
                    byte b = data[offset + i];
                    if (b >= 32 && b <= 126) {
                        sb.append((char) b);
                    } else {
                        sb.append('.');
                    }
                }
                sb.append("|");
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}
