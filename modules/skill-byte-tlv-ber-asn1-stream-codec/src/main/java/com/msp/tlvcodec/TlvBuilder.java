package com.msp.tlvcodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fluent builder for assembling nested {@link ConstructedTlv} trees.
 */
public final class TlvBuilder {

    private final TlvTag tag;
    private final List<TlvNode> children = new ArrayList<>();

    private TlvBuilder(TlvTag tag) {
        Objects.requireNonNull(tag, "tag must not be null");
        if (!tag.isConstructed()) {
            throw new IllegalArgumentException("TlvBuilder requires a constructed tag, got: " + tag);
        }
        this.tag = tag;
    }

    public static TlvBuilder constructed(TlvTag tag) {
        return new TlvBuilder(tag);
    }

    public static TlvBuilder constructedHex(String tagHex) {
        return new TlvBuilder(TlvTag.fromHex(tagHex));
    }

    public TlvBuilder child(TlvNode node) {
        children.add(Objects.requireNonNull(node, "child node must not be null"));
        return this;
    }

    public TlvBuilder primitive(TlvTag tag, byte[] value) {
        children.add(PrimitiveTlv.of(tag, value));
        return this;
    }

    public TlvBuilder primitiveHex(String tagHex, String valueHex) {
        children.add(PrimitiveTlv.ofHex(tagHex, valueHex));
        return this;
    }

    public TlvBuilder primitiveUtf8(TlvTag tag, String text) {
        children.add(PrimitiveTlv.ofUtf8(tag, text));
        return this;
    }

    public TlvBuilder nested(TlvTag nestedTag, Consumer<TlvBuilder> configurator) {
        Objects.requireNonNull(configurator, "configurator must not be null");
        TlvBuilder sub = new TlvBuilder(nestedTag);
        configurator.accept(sub);
        children.add(sub.build());
        return this;
    }

    public TlvBuilder nestedHex(String nestedTagHex, Consumer<TlvBuilder> configurator) {
        return nested(TlvTag.fromHex(nestedTagHex), configurator);
    }

    public ConstructedTlv build() {
        return new ConstructedTlv(tag, children);
    }
}
