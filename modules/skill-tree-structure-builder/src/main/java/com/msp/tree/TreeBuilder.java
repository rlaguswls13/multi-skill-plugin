package com.msp.tree;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builds hierarchical tree structures from flat data collections (e.g. database rows),
 * provides DFS/BFS traversals, flattens trees back into lists, and detects cycle loops and orphans.
 * <p>
 * Zero 3rd party runtime dependencies. Pure Java 21 standard library.
 */
public final class TreeBuilder {

    private TreeBuilder() {
        // utility class
    }

    /**
     * Result record containing built tree roots and detected orphan nodes.
     */
    public record TreeResult<T>(
            List<TreeNode<T>> roots,
            List<TreeNode<T>> orphans
    ) {
        public TreeResult {
            roots = (roots != null) ? List.copyOf(roots) : List.of();
            orphans = (orphans != null) ? List.copyOf(orphans) : List.of();
        }

        public boolean hasOrphans() {
            return !orphans.isEmpty();
        }
    }

    /**
     * Converts a flat collection of {@link TreeNode} into nested hierarchical root nodes.
     * Throws {@link IllegalStateException} if cycles or orphan nodes are detected.
     *
     * @param flatNodes flat collection of nodes
     * @param <T>       payload type
     * @return list of root nodes with nested children
     * @throws IllegalStateException if a cycle loop or orphan node is detected
     */
    public static <T> List<TreeNode<T>> buildTree(Collection<TreeNode<T>> flatNodes) {
        return buildTree(flatNodes, false);
    }

    /**
     * Converts a flat collection of {@link TreeNode} into nested hierarchical root nodes.
     *
     * @param flatNodes            flat collection of nodes
     * @param allowOrphansAsRoots  if true, orphan nodes whose parent does not exist are elevated as roots
     * @param <T>                  payload type
     * @return list of root nodes with nested children
     * @throws IllegalStateException if a cycle loop is detected, or if allowOrphans is false and orphans exist
     */
    public static <T> List<TreeNode<T>> buildTree(Collection<TreeNode<T>> flatNodes, boolean allowOrphansAsRoots) {
        TreeResult<T> result = buildResult(flatNodes);
        if (!allowOrphansAsRoots && result.hasOrphans()) {
            List<String> orphanIds = result.orphans().stream().map(TreeNode::id).toList();
            throw new IllegalStateException("Orphan nodes detected without parent in tree: " + orphanIds);
        }

        if (allowOrphansAsRoots && result.hasOrphans()) {
            List<TreeNode<T>> combined = new ArrayList<>(result.roots());
            combined.addAll(result.orphans());
            return Collections.unmodifiableList(combined);
        }

        return result.roots();
    }

    /**
     * Converts an arbitrary collection into nested hierarchical tree roots.
     */
    public static <E, T> List<TreeNode<T>> buildTree(
            Collection<E> items,
            Function<E, String> idExtractor,
            Function<E, String> parentIdExtractor,
            Function<E, T> dataExtractor
    ) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(idExtractor, "idExtractor must not be null");
        Objects.requireNonNull(parentIdExtractor, "parentIdExtractor must not be null");
        Objects.requireNonNull(dataExtractor, "dataExtractor must not be null");

        List<TreeNode<T>> flat = items.stream()
                .map(item -> new TreeNode<>(idExtractor.apply(item), parentIdExtractor.apply(item), dataExtractor.apply(item)))
                .toList();

        return buildTree(flat);
    }

    /**
     * Builds hierarchical tree result containing both nested roots and any detected orphans.
     * Strictly verifies that no cycle loops exist.
     *
     * @param flatNodes flat collection of nodes
     * @param <T>       payload type
     * @return TreeResult containing roots and orphans
     * @throws IllegalStateException if any cycle loop is detected
     */
    public static <T> TreeResult<T> buildResult(Collection<TreeNode<T>> flatNodes) {
        if (flatNodes == null || flatNodes.isEmpty()) {
            return new TreeResult<>(List.of(), List.of());
        }

        // Check for duplicates and cycle loops
        detectCycles(flatNodes);

        Map<String, MutableNode<T>> mutableMap = new LinkedHashMap<>();
        for (TreeNode<T> node : flatNodes) {
            if (node == null) {
                continue;
            }
            if (mutableMap.containsKey(node.id())) {
                throw new IllegalArgumentException("Duplicate node ID detected: " + node.id());
            }
            mutableMap.put(node.id(), new MutableNode<>(node.id(), node.parentId(), node.data()));
        }

        List<MutableNode<T>> rootNodes = new ArrayList<>();
        List<MutableNode<T>> orphanNodes = new ArrayList<>();

        for (MutableNode<T> node : mutableMap.values()) {
            String pId = node.parentId;
            if (pId == null || pId.isBlank()) {
                rootNodes.add(node);
            } else {
                MutableNode<T> parent = mutableMap.get(pId);
                if (parent != null) {
                    parent.children.add(node);
                } else {
                    orphanNodes.add(node);
                }
            }
        }

        List<TreeNode<T>> immutableRoots = rootNodes.stream().map(MutableNode::toImmutable).toList();
        List<TreeNode<T>> immutableOrphans = orphanNodes.stream().map(MutableNode::toImmutable).toList();

        return new TreeResult<>(immutableRoots, immutableOrphans);
    }

    /**
     * Traverses a tree in Depth-First Search (pre-order) manner.
     *
     * @param root    root node
     * @param visitor consumer invoked for each visited node
     * @param <T>     payload type
     */
    public static <T> void depthFirstSearch(TreeNode<T> root, Consumer<TreeNode<T>> visitor) {
        if (root == null || visitor == null) {
            return;
        }
        visitor.accept(root);
        for (TreeNode<T> child : root.children()) {
            depthFirstSearch(child, visitor);
        }
    }

    /**
     * Traverses multiple tree roots in Depth-First Search (pre-order) manner.
     */
    public static <T> void depthFirstSearch(List<TreeNode<T>> roots, Consumer<TreeNode<T>> visitor) {
        if (roots == null || visitor == null) {
            return;
        }
        for (TreeNode<T> root : roots) {
            depthFirstSearch(root, visitor);
        }
    }

    /**
     * Traverses a tree in Breadth-First Search (level-order) manner.
     *
     * @param root    root node
     * @param visitor consumer invoked for each visited node
     * @param <T>     payload type
     */
    public static <T> void breadthFirstSearch(TreeNode<T> root, Consumer<TreeNode<T>> visitor) {
        if (root == null || visitor == null) {
            return;
        }
        breadthFirstSearch(List.of(root), visitor);
    }

    /**
     * Traverses multiple tree roots in Breadth-First Search (level-order) manner.
     */
    public static <T> void breadthFirstSearch(List<TreeNode<T>> roots, Consumer<TreeNode<T>> visitor) {
        if (roots == null || visitor == null) {
            return;
        }
        Queue<TreeNode<T>> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            TreeNode<T> current = queue.poll();
            visitor.accept(current);
            queue.addAll(current.children());
        }
    }

    /**
     * Flattens a single tree root back into a linear list in pre-order DFS order.
     */
    public static <T> List<TreeNode<T>> flatten(TreeNode<T> root) {
        if (root == null) {
            return List.of();
        }
        return flatten(List.of(root));
    }

    /**
     * Flattens multiple tree roots back into a linear list in pre-order DFS order.
     */
    public static <T> List<TreeNode<T>> flatten(List<TreeNode<T>> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<TreeNode<T>> result = new ArrayList<>();
        depthFirstSearch(roots, node -> {
            // Strip children when flattening so each element represents the node itself
            result.add(new TreeNode<>(node.id(), node.parentId(), node.data(), List.of()));
        });
        return Collections.unmodifiableList(result);
    }

    /**
     * Detects any cycle loops within the nodes.
     *
     * @throws IllegalStateException if a cycle loop is found
     */
    private static <T> void detectCycles(Collection<TreeNode<T>> flatNodes) {
        Map<String, String> parentMap = new HashMap<>();
        for (TreeNode<T> node : flatNodes) {
            if (node != null) {
                if (node.id().equals(node.parentId())) {
                    throw new IllegalStateException("Self-referencing cycle detected on node id: " + node.id());
                }
                parentMap.put(node.id(), node.parentId());
            }
        }

        for (String startId : parentMap.keySet()) {
            Set<String> visitedInChain = new LinkedHashSet<>();
            String current = startId;

            while (current != null && !current.isBlank()) {
                if (!visitedInChain.add(current)) {
                    throw new IllegalStateException("Cycle loop detected: " + visitedInChain + " -> " + current);
                }
                current = parentMap.get(current);
            }
        }
    }

    private static final class MutableNode<T> {
        private final String id;
        private final String parentId;
        private final T data;
        private final List<MutableNode<T>> children = new ArrayList<>();

        MutableNode(String id, String parentId, T data) {
            this.id = id;
            this.parentId = parentId;
            this.data = data;
        }

        TreeNode<T> toImmutable() {
            List<TreeNode<T>> immutableChildren = children.stream()
                    .map(MutableNode::toImmutable)
                    .toList();
            return new TreeNode<>(id, parentId, data, immutableChildren);
        }
    }
}
