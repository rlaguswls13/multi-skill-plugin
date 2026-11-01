package com.msp.base91;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * An {@link InputStream} that decodes basE91 ASCII stream into raw binary data.
 */
public class Base91InputStream extends FilterInputStream {

    private final byte[] buffer = new byte[32];
    private int bufHead = 0;
    private int bufTail = 0;

    private int queue = 0;
    private int nbits = 0;
    private int value = -1;
    private boolean eof = false;

    public Base91InputStream(InputStream in) {
        super(Objects.requireNonNull(in, "in must not be null"));
    }

    @Override
    public int read() throws IOException {
        while (bufHead == bufTail) {
            if (eof) {
                return -1;
            }
            refillBuffer();
        }
        return buffer[bufHead++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }

        int c = read();
        if (c == -1) {
            return -1;
        }
        b[off] = (byte) c;

        int bytesRead = 1;
        while (bytesRead < len && bufHead < bufTail) {
            b[off + bytesRead++] = buffer[bufHead++];
        }
        return bytesRead;
    }

    private void refillBuffer() throws IOException {
        bufHead = 0;
        bufTail = 0;

        while (bufTail == 0) {
            int b = in.read();
            if (b == -1) {
                eof = true;
                if (value != -1) {
                    buffer[bufTail++] = (byte) (queue | (value << nbits));
                    value = -1;
                }
                return;
            }

            int charByte = b & 0xFF;
            // Ignore whitespace
            if (charByte == ' ' || charByte == '\t' || charByte == '\r' || charByte == '\n') {
                continue;
            }

            int tableIndex = (charByte < 128) ? Base91.ALPHABET.indexOf((char) charByte) : -1;
            if (tableIndex == -1) {
                throw new IOException(String.format("Invalid basE91 character '0x%02X' (%c)", charByte, (char) charByte));
            }

            if (value == -1) {
                value = tableIndex;
            } else {
                value += tableIndex * 91;
                queue |= value << nbits;
                nbits += (value & 8191) > 88 ? 13 : 14;
                do {
                    buffer[bufTail++] = (byte) queue;
                    queue >>>= 8;
                    nbits -= 8;
                } while (nbits > 7);
                value = -1;
            }
        }
    }
}
