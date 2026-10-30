package com.msp.tree;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable tree node representation.
 *
 * @param id       unique identifier of the node
 * @param parentId identifier of the parent node, or {@code null}/blank for root nodes
 * @param data     associated payload data
 * @param children immutable list of child nodes
 * @param <T>      type of the payload data
 */
public record TreeNode<T>(
        String id,
        String parentId,
        T data,
        List<TreeNode<T>> children
) {

    public TreeNode {
        Objects.requireNonNull(id, "id must not be null");
        children = (children != null) ? List.copyOf(children) : List.of();
    }

    /**
     * Convenience constructor creating a node without children.
     */
    public TreeNode(String id, String parentId, T data) {
        this(id, parentId, data, List.of());
    }

    /**
     * Returns true if this node has no parent (is a root node).
     */
    public boolean isRoot() {
        return parentId == null || parentId.isBlank();
    }

    /**
     * Returns true if this node has no children.
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Returns the count of direct children.
     */
    public int childCount() {
        return children.size();
    }

    /**
     * Creates a copy of this TreeNode with new children.
     */
    public TreeNode<T> withChildren(List<TreeNode<T>> newChildren) {
        return new TreeNode<>(this.id, this.parentId, this.data, newChildren);
    }

    // JavaBean style getter aliases for interoperability
    public String getId() {
        return id;
    }

    public String getParentId() {
        return parentId;
    }

    public T getData() {
        return data;
    }

    public List<TreeNode<T>> getChildren() {
        return children;
    }
}
