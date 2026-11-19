package com.msp.tlvcodec;

/**
 * Visitor interface for traversing a recursive BER-TLV node hierarchy.
 */
public interface TlvVisitor {

    /**
     * Invoked for each primitive TLV leaf node.
     */
    default void visitPrimitive(PrimitiveTlv node, int depth) {
    }

    /**
     * Invoked before traversing the children of a constructed TLV node.
     */
    default void enterConstructed(ConstructedTlv node, int depth) {
    }

    /**
     * Invoked after traversing all children of a constructed TLV node.
     */
    default void exitConstructed(ConstructedTlv node, int depth) {
    }
}
