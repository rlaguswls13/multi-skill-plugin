package com.msp.msgpack;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MessagePack binary deserializer complying with the official MessagePack specification.
 */
public final class MessagePackDecoder {

    public MessagePackDecoder() {}

    /**
     * Deserializes an object from a MessagePack byte array.
     */
    public Object decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return decode(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new RuntimeException("Deserialization failure", e);
        }
    }

    /**
     * Deserializes the next object from the given InputStream.
     */
    public Object decode(InputStream in) throws IOException {
        DataInputStream dis = (in instanceof DataInputStream d) ? d : new DataInputStream(in);
        int tag = dis.read();
        if (tag == -1) {
            throw new EOFException("Unexpected end of MessagePack stream");
        }

        // Positive fixint: 0x00 - 0x7f
        if ((tag & 0x80) == 0) {
            return (long) tag;
        }

        // Fixmap: 0x80 - 0x8f
        if ((tag & 0xf0) == 0x80) {
            int size = tag & 0x0f;
            return readMap(dis, size);
        }

        // Fixarray: 0x90 - 0x9f
        if ((tag & 0xf0) == 0x90) {
            int size = tag & 0x0f;
            return readArray(dis, size);
        }

        // Fixstr: 0xa0 - 0xbf
        if ((tag & 0xe0) == 0xa0) {
            int len = tag & 0x1f;
            return readString(dis, len);
        }

        // Negative fixint: 0xe0 - 0xff (-32 to -1)
        if ((tag & 0xe0) == 0xe0) {
            return (long) (byte) tag;
        }

        return switch (tag) {
            case 0xc0 -> null; // nil
            case 0xc2 -> Boolean.FALSE; // false
            case 0xc3 -> Boolean.TRUE; // true
            case 0xc4 -> readBinary(dis, dis.readUnsignedByte()); // bin 8
            case 0xc5 -> readBinary(dis, dis.readUnsignedShort()); // bin 16
            case 0xc6 -> readBinary(dis, dis.readInt()); // bin 32
            case 0xca -> dis.readFloat(); // float 32
            case 0xcb -> dis.readDouble(); // float 64
            case 0xcc -> (long) dis.readUnsignedByte(); // uint 8
            case 0xcd -> (long) dis.readUnsignedShort(); // uint 16
            case 0xce -> (long) dis.readInt() & 0xFFFFFFFFL; // uint 32
            case 0xcf -> dis.readLong(); // uint 64
            case 0xd0 -> (long) dis.readByte(); // int 8
            case 0xd1 -> (long) dis.readShort(); // int 16
            case 0xd2 -> (long) dis.readInt(); // int 32
            case 0xd3 -> dis.readLong(); // int 64
            case 0xd9 -> readString(dis, dis.readUnsignedByte()); // str 8
            case 0xda -> readString(dis, dis.readUnsignedShort()); // str 16
            case 0xdb -> readString(dis, dis.readInt()); // str 32
            case 0xdc -> readArray(dis, dis.readUnsignedShort()); // array 16
            case 0xdd -> readArray(dis, dis.readInt()); // array 32
            case 0xde -> readMap(dis, dis.readUnsignedShort()); // map 16
            case 0xdf -> readMap(dis, dis.readInt()); // map 32
            default -> throw new IllegalArgumentException("Unsupported MessagePack tag: 0x" + Integer.toHexString(tag));
        };
    }

    private String readString(DataInputStream dis, int len) throws IOException {
        if (len < 0) {
            throw new IllegalArgumentException("Negative string length: " + len);
        }
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] readBinary(DataInputStream dis, int len) throws IOException {
        if (len < 0) {
            throw new IllegalArgumentException("Negative binary length: " + len);
        }
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return bytes;
    }

    private List<Object> readArray(DataInputStream dis, int size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Negative array size: " + size);
        }
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(decode(dis));
        }
        return list;
    }

    private Map<Object, Object> readMap(DataInputStream dis, int size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Negative map size: " + size);
        }
        Map<Object, Object> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            Object key = decode(dis);
            Object value = decode(dis);
            map.put(key, value);
        }
        return map;
    }
}
