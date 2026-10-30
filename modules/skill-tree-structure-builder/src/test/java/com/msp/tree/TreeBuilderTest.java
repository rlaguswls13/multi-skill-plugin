package com.msp.tree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TreeBuilderTest {

    record Category(String id, String parentId, String name) {}

    @Test
    @DisplayName("Build nested tree structure from flat list")
    void testBuildTree() {
        List<TreeNode<String>> flat = List.of(
                new TreeNode<>("electronics", null, "Electronics"),
                new TreeNode<>("laptops", "electronics", "Laptops"),
                new TreeNode<>("gaming-laptops", "laptops", "Gaming Laptops"),
                new TreeNode<>("phones", "electronics", "Smartphones"),
                new TreeNode<>("books", null, "Books")
        );

        List<TreeNode<String>> roots = TreeBuilder.buildTree(flat);
        assertEquals(2, roots.size(), "Should have 2 root nodes");

        TreeNode<String> electronics = roots.get(0);
        assertEquals("electronics", electronics.id());
        assertTrue(electronics.isRoot());
        assertFalse(electronics.isLeaf());
        assertEquals(2, electronics.children().size());

        TreeNode<String> laptops = electronics.children().get(0);
        assertEquals("laptops", laptops.id());
        assertEquals(1, laptops.children().size());
        assertEquals("gaming-laptops", laptops.children().get(0).id());
        assertTrue(laptops.children().get(0).isLeaf());

        TreeNode<String> books = roots.get(1);
        assertEquals("books", books.id());
        assertTrue(books.isLeaf());
    }

    @Test
    @DisplayName("DFS and BFS traversal order verification")
    void testTraversals() {
        // Tree:
        //      A
        //    /   \
        //   B     C
        //  / \
        // D   E
        List<TreeNode<String>> flat = List.of(
                new TreeNode<>("A", null, "A"),
                new TreeNode<>("B", "A", "B"),
                new TreeNode<>("C", "A", "C"),
                new TreeNode<>("D", "B", "D"),
                new TreeNode<>("E", "B", "E")
        );

        TreeNode<String> root = TreeBuilder.buildTree(flat).get(0);

        // DFS (pre-order): A -> B -> D -> E -> C
        List<String> dfsOrder = new ArrayList<>();
        TreeBuilder.depthFirstSearch(root, node -> dfsOrder.add(node.id()));
        assertEquals(List.of("A", "B", "D", "E", "C"), dfsOrder);

        // BFS (level-order): A -> B -> C -> D -> E
        List<String> bfsOrder = new ArrayList<>();
        TreeBuilder.breadthFirstSearch(root, node -> bfsOrder.add(node.id()));
        assertEquals(List.of("A", "B", "C", "D", "E"), bfsOrder);
    }

    @Test
    @DisplayName("Flatten tree back into flat list")
    void testFlatten() {
        List<TreeNode<String>> flat = List.of(
                new TreeNode<>("1", null, "Root"),
                new TreeNode<>("2", "1", "Child")
        );
        List<TreeNode<String>> roots = TreeBuilder.buildTree(flat);
        List<TreeNode<String>> flattened = TreeBuilder.flatten(roots);

        assertEquals(2, flattened.size());
        assertEquals("1", flattened.get(0).id());
        assertEquals("2", flattened.get(1).id());
        assertTrue(flattened.get(0).children().isEmpty());
    }

    @Test
    @DisplayName("Cycle detection: self cycle and loop cycle throw IllegalStateException")
    void testCycleDetection() {
        // Self-referencing node
        List<TreeNode<String>> selfCycle = List.of(
                new TreeNode<>("A", "A", "Self Loop")
        );
        assertThrows(IllegalStateException.class, () -> TreeBuilder.buildTree(selfCycle));

        // Circular loop: A -> B -> C -> A
        List<TreeNode<String>> circular = List.of(
                new TreeNode<>("A", "C", "Node A"),
                new TreeNode<>("B", "A", "Node B"),
                new TreeNode<>("C", "B", "Node C")
        );
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> TreeBuilder.buildTree(circular));
        assertTrue(ex.getMessage().contains("Cycle loop detected"));
    }

    @Test
    @DisplayName("Orphan node detection")
    void testOrphanDetection() {
        List<TreeNode<String>> nodesWithOrphan = List.of(
                new TreeNode<>("root", null, "Root"),
                new TreeNode<>("orphan", "non-existent-parent", "Lost Child")
        );

        // Strict buildTree throws exception on orphans
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                TreeBuilder.buildTree(nodesWithOrphan, false)
        );
        assertTrue(ex.getMessage().contains("Orphan nodes detected"));

        // buildResult captures orphans safely without exception
        TreeBuilder.TreeResult<String> result = TreeBuilder.buildResult(nodesWithOrphan);
        assertEquals(1, result.roots().size());
        assertEquals("root", result.roots().get(0).id());
        assertEquals(1, result.orphans().size());
        assertEquals("orphan", result.orphans().get(0).id());

        // Allow orphans elevated as roots
        List<TreeNode<String>> elevated = TreeBuilder.buildTree(nodesWithOrphan, true);
        assertEquals(2, elevated.size());
    }

    @Test
    @DisplayName("Build tree from generic entity objects")
    void testBuildFromCustomEntity() {
        List<Category> categories = List.of(
                new Category("10", null, "Parent Category"),
                new Category("20", "10", "Subcategory")
        );

        List<TreeNode<String>> roots = TreeBuilder.buildTree(
                categories,
                Category::id,
                Category::parentId,
                Category::name
        );

        assertEquals(1, roots.size());
        assertEquals("Parent Category", roots.get(0).data());
        assertEquals(1, roots.get(0).children().size());
        assertEquals("Subcategory", roots.get(0).children().get(0).data());
    }

    @Test
    @DisplayName("Duplicate ID throws IllegalArgumentException")
    void testDuplicateId() {
        List<TreeNode<String>> duplicates = List.of(
                new TreeNode<>("same", null, "First"),
                new TreeNode<>("same", null, "Second")
        );
        assertThrows(IllegalArgumentException.class, () -> TreeBuilder.buildTree(duplicates));
    }
}
