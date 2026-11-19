package com.msp.msgpack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * MessagePack binary serializer complying with the official MessagePack specification.
 */
public final class MessagePackEncoder {

    public MessagePackEncoder() {}

    /**
     * Serializes any supported Java object to a MessagePack byte array.
     */
    public byte[] encode(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            encode(obj, baos);
        } catch (IOException e) {
            throw new RuntimeException("Serialization failure", e);
        }
        return baos.toByteArray();
    }

    /**
     * Serializes an object directly into the provided OutputStream.
     */
    public void encode(Object obj, OutputStream out) throws IOException {
        DataOutputStream dos = (out instanceof DataOutputStream d) ? d : new DataOutputStream(out);

        if (obj == null) {
            dos.writeByte(0xc0); // nil
            return;
        }

        if (obj instanceof Boolean b) {
            dos.writeByte(b ? 0xc3 : 0xc2); // true / false
            return;
        }

        if (obj instanceof Number num) {
            encodeNumber(num, dos);
            return;
        }

        if (obj instanceof CharSequence cs) {
            encodeString(cs.toString(), dos);
            return;
        }

        if (obj instanceof Character c) {
            encodeString(String.valueOf(c), dos);
            return;
        }

        if (obj instanceof byte[] bytes) {
            encodeBinary(bytes, dos);
            return;
        }

        if (obj instanceof Map<?, ?> map) {
            encodeMap(map, dos);
            return;
        }

        if (obj instanceof Collection<?> coll) {
            encodeCollection(coll, dos);
            return;
        }

        if (obj instanceof Object[] array) {
            encodeArray(array, dos);
            return;
        }

        throw new IllegalArgumentException("Unsupported type for MessagePack encoding: " + obj.getClass().getName());
    }

    private void encodeNumber(Number num, DataOutputStream dos) throws IOException {
        if (num instanceof Float f) {
            dos.writeByte(0xca); // float 32
            dos.writeFloat(f);
            return;
        }
        if (num instanceof Double d) {
            dos.writeByte(0xcb); // float 64
            dos.writeDouble(d);
            return;
        }

        long val = num.longValue();
        if (val >= 0) {
            if (val <= 127) {
                // positive fixint: 0x00 - 0x7f
                dos.writeByte((int) val);
            } else if (val <= 0xFFL) {
                // uint 8
                dos.writeByte(0xcc);
                dos.writeByte((int) val);
            } else if (val <= 0xFFFFL) {
                // uint 16
                dos.writeByte(0xcd);
                dos.writeShort((int) val);
            } else if (val <= 0xFFFFFFFFL) {
                // uint 32
                dos.writeByte(0xce);
                dos.writeInt((int) val);
            } else {
                // uint 64 / int 64
                dos.writeByte(0xcf);
                dos.writeLong(val);
            }
        } else {
            if (val >= -32) {
                // negative fixint: 0xe0 - 0xff (-32 to -1)
                dos.writeByte((int) val);
            } else if (val >= -128) {
                // int 8
                dos.writeByte(0xd0);
                dos.writeByte((int) val);
            } else if (val >= -32768) {
                // int 16
                dos.writeByte(0xd1);
                dos.writeShort((int) val);
            } else if (val >= Integer.MIN_VALUE) {
                // int 32
                dos.writeByte(0xd2);
                dos.writeInt((int) val);
            } else {
                // int 64
                dos.writeByte(0xd3);
                dos.writeLong(val);
            }
        }
    }

    private void encodeString(String str, DataOutputStream dos) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        int len = bytes.length;
        if (len <= 31) {
            // fixstr: 0xa0 | len
            dos.writeByte(0xa0 | len);
        } else if (len <= 0xFF) {
            // str 8
            dos.writeByte(0xd9);
            dos.writeByte(len);
        } else if (len <= 0xFFFF) {
            // str 16
            dos.writeByte(0xda);
            dos.writeShort(len);
        } else {
            // str 32
            dos.writeByte(0xdb);
            dos.writeInt(len);
        }
        dos.write(bytes);
    }

    private void encodeBinary(byte[] bytes, DataOutputStream dos) throws IOException {
        int len = bytes.length;
        if (len <= 0xFF) {
            // bin 8
            dos.writeByte(0xc4);
            dos.writeByte(len);
        } else if (len <= 0xFFFF) {
            // bin 16
            dos.writeByte(0xc5);
            dos.writeShort(len);
        } else {
            // bin 32
            dos.writeByte(0xc6);
            dos.writeInt(len);
        }
        dos.write(bytes);
    }

    private void encodeCollection(Collection<?> coll, DataOutputStream dos) throws IOException {
        int size = coll.size();
        if (size <= 15) {
            // fixarray: 0x90 | size
            dos.writeByte(0x90 | size);
        } else if (size <= 0xFFFF) {
            // array 16
            dos.writeByte(0xdc);
            dos.writeShort(size);
        } else {
            // array 32
            dos.writeByte(0xdd);
            dos.writeInt(size);
        }
        for (Object item : coll) {
            encode(item, dos);
        }
    }

    private void encodeArray(Object[] array, DataOutputStream dos) throws IOException {
        int size = array.length;
        if (size <= 15) {
            dos.writeByte(0x90 | size);
        } else if (size <= 0xFFFF) {
            dos.writeByte(0xdc);
            dos.writeShort(size);
        } else {
            dos.writeByte(0xdd);
            dos.writeInt(size);
        }
        for (Object item : array) {
            encode(item, dos);
        }
    }

    private void encodeMap(Map<?, ?> map, DataOutputStream dos) throws IOException {
        int size = map.size();
        if (size <= 15) {
            // fixmap: 0x80 | size
            dos.writeByte(0x80 | size);
        } else if (size <= 0xFFFF) {
            // map 16
            dos.writeByte(0xde);
            dos.writeShort(size);
        } else {
            // map 32
            dos.writeByte(0xdf);
            dos.writeInt(size);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            encode(entry.getKey(), dos);
            encode(entry.getValue(), dos);
        }
    }
}
