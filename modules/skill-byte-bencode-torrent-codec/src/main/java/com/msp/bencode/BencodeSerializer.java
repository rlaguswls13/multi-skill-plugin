package com.msp.bencode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Serializer for BitTorrent Bencode format (BEP 0003).
 */
public final class BencodeSerializer {

    private final OutputStream out;

    public BencodeSerializer(OutputStream out) {
        this.out = Objects.requireNonNull(out, "out cannot be null");
    }

    public static byte[] serialize(BencodeValue value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            serialize(value, baos);
        } catch (IOException e) {
            throw new BencodeException("Error during bencode serialization", e);
        }
        return baos.toByteArray();
    }

    public static void serialize(BencodeValue value, OutputStream os) throws IOException {
        new BencodeSerializer(os).write(value);
    }

    public static byte[] serialize(Object javaObj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new BencodeSerializer(baos).writeObject(javaObj);
        } catch (IOException e) {
            throw new BencodeException("Error during bencode serialization of Java object", e);
        }
        return baos.toByteArray();
    }

    public void write(BencodeValue value) throws IOException {
        Objects.requireNonNull(value, "value cannot be null");
        switch (value) {
            case BencodeInteger bi -> writeInteger(bi.value());
            case BencodeString bs -> writeString(bs.getBytes());
            case BencodeList bl -> writeList(bl);
            case BencodeDict bd -> writeDict(bd);
        }
    }

    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            throw new BencodeException("Bencode does not support null values");
        }
        if (obj instanceof BencodeValue bv) {
            write(bv);
        } else if (obj instanceof Number num) {
            writeInteger(num.longValue());
        } else if (obj instanceof String str) {
            writeString(str.getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof byte[] bytes) {
            writeString(bytes);
        } else if (obj instanceof Iterable<?> iter) {
            out.write('l');
            for (Object item : iter) {
                writeObject(item);
            }
            out.write('e');
        } else if (obj instanceof Map<?, ?> map) {
            // Sort keys by raw byte comparison
            TreeMap<BencodeString, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                BencodeString key = toKey(entry.getKey());
                sorted.put(key, entry.getValue());
            }
            out.write('d');
            for (Map.Entry<BencodeString, Object> entry : sorted.entrySet()) {
                writeString(entry.getKey().getBytes());
                writeObject(entry.getValue());
            }
            out.write('e');
        } else {
            throw new BencodeException("Unsupported object type for Bencode: " + obj.getClass().getName());
        }
    }

    private void writeInteger(long val) throws IOException {
        out.write('i');
        out.write(Long.toString(val).getBytes(StandardCharsets.US_ASCII));
        out.write('e');
    }

    private void writeString(byte[] bytes) throws IOException {
        out.write(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        out.write(':');
        out.write(bytes);
    }

    private void writeList(BencodeList list) throws IOException {
        out.write('l');
        for (BencodeValue item : list) {
            write(item);
        }
        out.write('e');
    }

    private void writeDict(BencodeDict dict) throws IOException {
        out.write('d');
        // entries is already sorted TreeMap<BencodeString, BencodeValue>
        for (Map.Entry<BencodeString, BencodeValue> entry : dict.getEntries().entrySet()) {
            writeString(entry.getKey().getBytes());
            write(entry.getValue());
        }
        out.write('e');
    }

    private static BencodeString toKey(Object keyObj) {
        if (keyObj == null) {
            throw new BencodeException("Dictionary key cannot be null");
        }
        if (keyObj instanceof BencodeString bs) {
            return bs;
        }
        if (keyObj instanceof String str) {
            return BencodeString.of(str);
        }
        if (keyObj instanceof byte[] bytes) {
            return BencodeString.of(bytes);
        }
        return BencodeString.of(keyObj.toString());
    }
}
