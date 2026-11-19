package com.msp.tlvcodec;

/**
 * Sealed root type representing either a primitive or constructed ASN.1 BER-TLV node.
 */
public sealed interface TlvNode permits PrimitiveTlv, ConstructedTlv {

    /**
     * Returns the BER-TLV tag of this node.
     */
    TlvTag tag();

    /**
     * Returns {@code true} if this node is constructed (contains child TLV nodes).
     */
    default boolean isConstructed() {
        return tag().isConstructed();
    }

    /**
     * Accepts a {@link TlvVisitor} starting at depth 0.
     */
    default void accept(TlvVisitor visitor) {
        accept(visitor, 0);
    }

    /**
     * Accepts a {@link TlvVisitor} at the specified tree depth.
     */
    void accept(TlvVisitor visitor, int depth);
}
