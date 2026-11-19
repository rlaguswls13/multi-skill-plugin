package com.msp.bencode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Representation of a Bencode list: l<contents>e.
 */
public record BencodeList(List<BencodeValue> values) implements BencodeValue, Iterable<BencodeValue> {

    public BencodeList {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public static BencodeList of(BencodeValue... elements) {
        if (elements == null || elements.length == 0) {
            return new BencodeList(List.of());
        }
        return new BencodeList(List.of(elements));
    }

    public static BencodeList of(List<BencodeValue> elements) {
        return new BencodeList(elements);
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public BencodeValue get(int index) {
        return values.get(index);
    }

    @Override
    public Iterator<BencodeValue> iterator() {
        return values.iterator();
    }
}
