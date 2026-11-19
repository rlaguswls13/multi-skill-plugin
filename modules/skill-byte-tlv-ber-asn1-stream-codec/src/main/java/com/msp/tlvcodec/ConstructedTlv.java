package com.msp.tlvcodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable constructed BER-TLV container node holding an ordered list of child {@link TlvNode} elements.
 */
public final class ConstructedTlv implements TlvNode {

    private final TlvTag tag;
    private final List<TlvNode> children;

    public ConstructedTlv(TlvTag tag, List<TlvNode> children) {
        Objects.requireNonNull(tag, "tag must not be null");
        Objects.requireNonNull(children, "children must not be null");
        if (!tag.isConstructed()) {
            throw new IllegalArgumentException("ConstructedTlv requires a constructed tag, got primitive tag: " + tag);
        }
        this.tag = tag;
        this.children = List.copyOf(children);
    }

    public static ConstructedTlv of(TlvTag tag, List<TlvNode> children) {
        return new ConstructedTlv(tag, children);
    }

    public static ConstructedTlv of(TlvTag tag, TlvNode... children) {
        return new ConstructedTlv(tag, List.of(children));
    }

    @Override
    public TlvTag tag() {
        return tag;
    }

    public List<TlvNode> children() {
        return children;
    }

    /**
     * Depth-first search for the first descendant node matching {@code targetTag}.
     */
    public Optional<TlvNode> findFirst(TlvTag targetTag) {
        Objects.requireNonNull(targetTag, "targetTag must not be null");
        for (TlvNode child : children) {
            if (child.tag().equals(targetTag)) {
                return Optional.of(child);
            }
            if (child instanceof ConstructedTlv nested) {
                Optional<TlvNode> found = nested.findFirst(targetTag);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Depth-first search returning all descendant nodes matching {@code targetTag}.
     */
    public List<TlvNode> findAll(TlvTag targetTag) {
        Objects.requireNonNull(targetTag, "targetTag must not be null");
        List<TlvNode> matches = new ArrayList<>();
        collectMatches(targetTag, matches);
        return List.copyOf(matches);
    }

    private void collectMatches(TlvTag targetTag, List<TlvNode> out) {
        for (TlvNode child : children) {
            if (child.tag().equals(targetTag)) {
                out.add(child);
            }
            if (child instanceof ConstructedTlv nested) {
                nested.collectMatches(targetTag, out);
            }
        }
    }

    @Override
    public void accept(TlvVisitor visitor, int depth) {
        Objects.requireNonNull(visitor, "visitor must not be null");
        visitor.enterConstructed(this, depth);
        for (TlvNode child : children) {
            child.accept(visitor, depth + 1);
        }
        visitor.exitConstructed(this, depth);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConstructedTlv other)) return false;
        return tag.equals(other.tag) && children.equals(other.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, children);
    }

    @Override
    public String toString() {
        return "ConstructedTlv[tag=" + tag.toHex() + ", children=" + children.size() + "]";
    }
}
