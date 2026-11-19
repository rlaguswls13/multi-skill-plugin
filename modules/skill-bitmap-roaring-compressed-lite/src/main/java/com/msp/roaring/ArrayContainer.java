package com.msp.roaring;

import java.util.Arrays;

/**
 * Sparse 16-bit container backed by a sorted {@code short[]} array when cardinality &lt;= 4096.
 */
public final class ArrayContainer implements Container {

    private short[] content;
    private int size;

    public ArrayContainer() {
        this(4);
    }

    public ArrayContainer(int initialCapacity) {
        this.content = new short[Math.max(4, initialCapacity)];
        this.size = 0;
    }

    ArrayContainer(short[] content, int size) {
        this.content = content;
        this.size = size;
    }

    short[] rawContent() {
        return content;
    }

    int rawSize() {
        return size;
    }

    private static int unsignedBinarySearch(short[] array, int begin, int end, int targetUnsigned) {
        int low = begin;
        int high = end - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = Short.toUnsignedInt(array[mid]);
            if (midVal < targetUnsigned) {
                low = mid + 1;
            } else if (midVal > targetUnsigned) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @Override
    public Container add(int low16) {
        int target = low16 & 0xFFFF;
        int idx = unsignedBinarySearch(content, 0, size, target);
        if (idx >= 0) {
            return this;
        }
        if (size >= MAX_ARRAY_CARDINALITY) {
            BitmapContainer bc = toBitmapContainer();
            bc.add(target);
            return bc;
        }
        int insertPos = -idx - 1;
        if (size >= content.length) {
            content = Arrays.copyOf(content, Math.min(MAX_ARRAY_CARDINALITY, Math.max(8, content.length * 2)));
        }
        if (insertPos < size) {
            System.arraycopy(content, insertPos, content, insertPos + 1, size - insertPos);
        }
        content[insertPos] = (short) target;
        size++;
        return this;
    }

    @Override
    public Container addRange(int startLow16, int endLow16Exclusive) {
        if (startLow16 >= endLow16Exclusive) {
            return this;
        }
        int rangeLen = endLow16Exclusive - startLow16;
        if (size + rangeLen > MAX_ARRAY_CARDINALITY) {
            BitmapContainer bc = toBitmapContainer();
            return bc.addRange(startLow16, endLow16Exclusive);
        }
        Container c = this;
        for (int v = startLow16; v < endLow16Exclusive; v++) {
            c = c.add(v);
        }
        return c;
    }

    @Override
    public Container remove(int low16) {
        int target = low16 & 0xFFFF;
        int idx = unsignedBinarySearch(content, 0, size, target);
        if (idx >= 0) {
            int numMoved = size - idx - 1;
            if (numMoved > 0) {
                System.arraycopy(content, idx + 1, content, idx, numMoved);
            }
            size--;
        }
        return this;
    }

    @Override
    public boolean contains(int low16) {
        return unsignedBinarySearch(content, 0, size, low16 & 0xFFFF) >= 0;
    }

    @Override
    public int cardinality() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Container and(Container other) {
        if (other instanceof ArrayContainer ac) {
            short[] res = new short[Math.min(this.size, ac.size)];
            int i = 0, j = 0, k = 0;
            while (i < this.size && j < ac.size) {
                int v1 = Short.toUnsignedInt(this.content[i]);
                int v2 = Short.toUnsignedInt(ac.content[j]);
                if (v1 == v2) {
                    res[k++] = (short) v1;
                    i++;
                    j++;
                } else if (v1 < v2) {
                    i++;
                } else {
                    j++;
                }
            }
            return new ArrayContainer(res, k);
        } else {
            short[] res = new short[this.size];
            int k = 0;
            for (int i = 0; i < this.size; i++) {
                int v = Short.toUnsignedInt(this.content[i]);
                if (other.contains(v)) {
                    res[k++] = (short) v;
                }
            }
            return new ArrayContainer(res, k);
        }
    }

    @Override
    public Container or(Container other) {
        if (other instanceof ArrayContainer ac) {
            int maxPossible = this.size + ac.size;
            if (maxPossible <= MAX_ARRAY_CARDINALITY) {
                short[] res = new short[maxPossible];
                int i = 0, j = 0, k = 0;
                while (i < this.size && j < ac.size) {
                    int v1 = Short.toUnsignedInt(this.content[i]);
                    int v2 = Short.toUnsignedInt(ac.content[j]);
                    if (v1 == v2) {
                        res[k++] = (short) v1;
                        i++;
                        j++;
                    } else if (v1 < v2) {
                        res[k++] = (short) v1;
                        i++;
                    } else {
                        res[k++] = (short) v2;
                        j++;
                    }
                }
                while (i < this.size) {
                    res[k++] = this.content[i++];
                }
                while (j < ac.size) {
                    res[k++] = ac.content[j++];
                }
                return new ArrayContainer(res, k);
            }
        }
        return this.toBitmapContainer().or(other);
    }

    @Override
    public Container xor(Container other) {
        if (other instanceof ArrayContainer ac) {
            int maxPossible = this.size + ac.size;
            if (maxPossible <= MAX_ARRAY_CARDINALITY) {
                short[] res = new short[maxPossible];
                int i = 0, j = 0, k = 0;
                while (i < this.size && j < ac.size) {
                    int v1 = Short.toUnsignedInt(this.content[i]);
                    int v2 = Short.toUnsignedInt(ac.content[j]);
                    if (v1 == v2) {
                        i++;
                        j++;
                    } else if (v1 < v2) {
                        res[k++] = (short) v1;
                        i++;
                    } else {
                        res[k++] = (short) v2;
                        j++;
                    }
                }
                while (i < this.size) {
                    res[k++] = this.content[i++];
                }
                while (j < ac.size) {
                    res[k++] = ac.content[j++];
                }
                return new ArrayContainer(res, k);
            }
        }
        return this.toBitmapContainer().xor(other);
    }

    @Override
    public Container andNot(Container other) {
        if (other instanceof ArrayContainer ac) {
            short[] res = new short[this.size];
            int i = 0, j = 0, k = 0;
            while (i < this.size && j < ac.size) {
                int v1 = Short.toUnsignedInt(this.content[i]);
                int v2 = Short.toUnsignedInt(ac.content[j]);
                if (v1 == v2) {
                    i++;
                    j++;
                } else if (v1 < v2) {
                    res[k++] = (short) v1;
                    i++;
                } else {
                    j++;
                }
            }
            while (i < this.size) {
                res[k++] = this.content[i++];
            }
            return new ArrayContainer(res, k);
        } else {
            short[] res = new short[this.size];
            int k = 0;
            for (int i = 0; i < this.size; i++) {
                int v = Short.toUnsignedInt(this.content[i]);
                if (!other.contains(v)) {
                    res[k++] = (short) v;
                }
            }
            return new ArrayContainer(res, k);
        }
    }

    @Override
    public Container optimize() {
        if (size == 0) {
            return this;
        }
        int runs = countRuns();
        // Each run takes 2 shorts (4 bytes); each array item takes 1 short (2 bytes).
        if (runs * 2 < size) {
            return RunContainer.fromArrayContainer(this);
        }
        return this;
    }

    int countRuns() {
        if (size == 0) {
            return 0;
        }
        int runs = 1;
        int prev = Short.toUnsignedInt(content[0]);
        for (int i = 1; i < size; i++) {
            int curr = Short.toUnsignedInt(content[i]);
            if (curr != prev + 1) {
                runs++;
            }
            prev = curr;
        }
        return runs;
    }

    @Override
    public BitmapContainer toBitmapContainer() {
        BitmapContainer bc = new BitmapContainer();
        for (int i = 0; i < size; i++) {
            bc.addInPlace(Short.toUnsignedInt(content[i]));
        }
        return bc;
    }

    @Override
    public ArrayContainer toArrayContainer() {
        return (ArrayContainer) copy();
    }

    @Override
    public int fillArray(int highBitsShifted, int[] dest, int offset) {
        for (int i = 0; i < size; i++) {
            dest[offset + i] = highBitsShifted | Short.toUnsignedInt(content[i]);
        }
        return offset + size;
    }

    @Override
    public Container copy() {
        return new ArrayContainer(Arrays.copyOf(content, size), size);
    }
}
