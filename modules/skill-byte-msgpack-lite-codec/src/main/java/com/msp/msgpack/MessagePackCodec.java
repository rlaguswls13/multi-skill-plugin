package com.msp.msgpack;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * High-level static codec facade for MessagePack encoding and decoding.
 */
public final class MessagePackCodec {

    private static final MessagePackEncoder ENCODER = new MessagePackEncoder();
    private static final MessagePackDecoder DECODER = new MessagePackDecoder();

    private MessagePackCodec() {}

    /**
     * Serializes an object to MessagePack binary format.
     */
    public static byte[] encode(Object obj) {
        return ENCODER.encode(obj);
    }

    /**
     * Serializes an object to the provided OutputStream.
     */
    public static void encode(Object obj, OutputStream out) throws IOException {
        ENCODER.encode(obj, out);
    }

    /**
     * Deserializes a MessagePack byte array to its Java equivalent.
     */
    public static Object decode(byte[] bytes) {
        return DECODER.decode(bytes);
    }

    /**
     * Deserializes the next object from the given InputStream.
     */
    public static Object decode(InputStream in) throws IOException {
        return DECODER.decode(in);
    }

    /**
     * Deserializes a MessagePack byte array into a typed Map.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> decodeMap(byte[] bytes) {
        Object result = decode(bytes);
        if (result == null) return null;
        if (result instanceof Map<?, ?> map) {
            return (Map<K, V>) map;
        }
        throw new ClassCastException("Decoded object is not a Map: " + result.getClass());
    }

    /**
     * Deserializes a MessagePack byte array into a typed List.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> decodeList(byte[] bytes) {
        Object result = decode(bytes);
        if (result == null) return null;
        if (result instanceof List<?> list) {
            return (List<T>) list;
        }
        throw new ClassCastException("Decoded object is not a List: " + result.getClass());
    }
}
