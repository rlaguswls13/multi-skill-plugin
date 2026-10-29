package com.msp.profiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfilerTest {

    @Test
    @DisplayName("Hierarchical nested steps record parent-child relationships and elapsed times")
    void testHierarchicalProfiling() throws InterruptedException {
        Profiler profiler = Profiler.start("UserLogin");

        try (ProfileScope s1 = profiler.step("validateInput")) {
            Thread.sleep(10);
        }

        try (ProfileScope s2 = profiler.step("dbLookup")) {
            Thread.sleep(15);
            try (ProfileScope s21 = profiler.step("queryUser")) {
                Thread.sleep(10);
            }
        }

        profiler.stop();

        ProfileNode root = profiler.getRoot();
        assertEquals("UserLogin", root.getName());
        assertFalse(root.isRunning());
        assertTrue(root.getElapsedMillis() >= 30.0);

        List<ProfileNode> rootChildren = root.getChildren();
        assertEquals(2, rootChildren.size());

        ProfileNode valNode = rootChildren.get(0);
        assertEquals("validateInput", valNode.getName());
        assertTrue(valNode.getChildren().isEmpty());

        ProfileNode dbNode = rootChildren.get(1);
        assertEquals("dbLookup", dbNode.getName());
        assertEquals(1, dbNode.getChildren().size());
        assertEquals("queryUser", dbNode.getChildren().get(0).getName());
    }

    @Test
    @DisplayName("ASCII and Unicode tree pretty printers generate structured output")
    void testTreePrettyPrinters() throws InterruptedException {
        Profiler profiler = Profiler.start("ApiRequest");

        try (ProfileScope s1 = profiler.step("auth")) {
            Thread.sleep(5);
        }
        try (ProfileScope s2 = profiler.step("process")) {
            try (ProfileScope s21 = profiler.step("subStep1")) {
                Thread.sleep(5);
            }
            try (ProfileScope s22 = profiler.step("subStep2")) {
                Thread.sleep(5);
            }
        }
        profiler.stop();

        // 1. Unicode tree
        String unicodeTree = profiler.printTree();
        assertNotNull(unicodeTree);
        assertTrue(unicodeTree.contains("[ApiRequest]"));
        assertTrue(unicodeTree.contains("├── [auth]"));
        assertTrue(unicodeTree.contains("└── [process]"));
        assertTrue(unicodeTree.contains("├── [subStep1]"));
        assertTrue(unicodeTree.contains("└── [subStep2]"));
        assertTrue(unicodeTree.contains("%"));

        // 2. ASCII tree
        String asciiTree = profiler.printAsciiTree();
        assertNotNull(asciiTree);
        assertTrue(asciiTree.contains("+-- [auth]"));
        assertTrue(asciiTree.contains("\\-- [process]"));
    }

    @Test
    @DisplayName("ThreadLocal trace startTrace, enter, and stopTrace work cleanly")
    void testThreadLocalTrace() throws InterruptedException {
        Profiler profiler = Profiler.startTrace("CheckoutFlow");
        assertTrue(Profiler.current().isPresent());

        try (ProfileScope s = Profiler.enter("validateCart")) {
            Thread.sleep(5);
        }

        try (ProfileScope s = Profiler.enter("chargeCard")) {
            Thread.sleep(10);
        }

        String tree = Profiler.printCurrentTrace();
        assertTrue(tree.contains("CheckoutFlow"));
        assertTrue(tree.contains("validateCart"));
        assertTrue(tree.contains("chargeCard"));

        ProfileNode root = Profiler.stopTrace();
        assertNotNull(root);
        assertEquals("CheckoutFlow", root.getName());
        assertTrue(Profiler.current().isEmpty());
    }

    @Test
    @DisplayName("Scope properly closes and measures time even when exception is thrown")
    void testScopeClosesOnException() {
        Profiler profiler = Profiler.start("ErrorHandling");

        assertThrows(IllegalStateException.class, () -> {
            try (ProfileScope s = profiler.step("faultyOperation")) {
                throw new IllegalStateException("Simulated error");
            }
        });

        profiler.stop();
        assertEquals(1, profiler.getRoot().getChildren().size());
        ProfileNode node = profiler.getRoot().getChildren().get(0);
        assertFalse(node.isRunning());
        assertTrue(node.getElapsedNanos() >= 0);
    }
}
