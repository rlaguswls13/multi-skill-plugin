package com.msp.bloom;

/**
 * Sink for receiving serialized primitive data from a {@link Funnel}.
 */
public interface Sink {

    Sink putByte(byte b);

    Sink putBytes(byte[] bytes);

    Sink putBytes(byte[] bytes, int off, int len);

    Sink putInt(int value);

    Sink putLong(long value);

    Sink putString(CharSequence charSequence);
}
