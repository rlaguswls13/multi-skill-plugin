package com.msp.base91;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * An {@link OutputStream} that encodes binary data into basE91 ASCII output.
 */
public class Base91OutputStream extends FilterOutputStream {

    private int queue = 0;
    private int nbits = 0;
    private boolean closed = false;

    public Base91OutputStream(OutputStream out) {
        super(Objects.requireNonNull(out, "out must not be null"));
    }

    @Override
    public void write(int b) throws IOException {
        queue |= (b & 0xFF) << nbits;
        nbits += 8;
        if (nbits > 13) {
            int value = queue & 8191;
            if (value > 88) {
                queue >>>= 13;
                nbits -= 13;
            } else {
                value = queue & 16383;
                queue >>>= 14;
                nbits -= 14;
            }
            out.write(Base91.ENCODING_TABLE[value % 91]);
            out.write(Base91.ENCODING_TABLE[value / 91]);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        for (int i = 0; i < len; i++) {
            write(b[off + i]);
        }
    }

    public void finish() throws IOException {
        if (nbits > 0) {
            out.write(Base91.ENCODING_TABLE[queue % 91]);
            if (nbits > 7 || queue > 90) {
                out.write(Base91.ENCODING_TABLE[queue / 91]);
            }
            queue = 0;
            nbits = 0;
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                finish();
                out.flush();
            } finally {
                closed = true;
                super.close();
            }
        }
    }
}
