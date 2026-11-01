package com.msp.etag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EntityTag Unit Tests")
class EntityTagTest {

    @Test
    @DisplayName("Parses strong and weak ETags correctly")
    void testParseTags() {
        EntityTag strong = EntityTag.parse("\"xyzzy\"");
        assertFalse(strong.isWeak());
        assertFalse(strong.isWildcard());
        assertEquals("xyzzy", strong.getOpaqueTag());
        assertEquals("\"xyzzy\"", strong.format());

        EntityTag weak = EntityTag.parse("W/\"xyzzy\"");
        assertTrue(weak.isWeak());
        assertFalse(weak.isWildcard());
        assertEquals("xyzzy", weak.getOpaqueTag());
        assertEquals("W/\"xyzzy\"", weak.format());

        EntityTag wildcard = EntityTag.parse("*");
        assertTrue(wildcard.isWildcard());
        assertEquals("*", wildcard.format());
    }

    @Test
    @DisplayName("Parses comma-separated list of ETags")
    void testParseList() {
        String header = "\"tag1\", W/\"tag2\", \"tag3,with-comma\", *";
        List<EntityTag> tags = EntityTag.parseList(header);
        assertEquals(4, tags.size());

        assertEquals(EntityTag.strong("tag1"), tags.get(0));
        assertEquals(EntityTag.weak("tag2"), tags.get(1));
        assertEquals(EntityTag.strong("tag3,with-comma"), tags.get(2));
        assertEquals(EntityTag.wildcard(), tags.get(3));
    }

    @Test
    @DisplayName("Strong comparison follows RFC 7232 Section 2.3.2")
    void testStrongComparison() {
        EntityTag s1 = EntityTag.strong("abc");
        EntityTag s2 = EntityTag.strong("abc");
        EntityTag s3 = EntityTag.strong("def");
        EntityTag w1 = EntityTag.weak("abc");

        assertTrue(s1.strongCompare(s2));
        assertFalse(s1.strongCompare(s3));
        assertFalse(s1.strongCompare(w1), "Strong comparison must be false if either is weak");
        assertFalse(w1.strongCompare(w1), "Strong comparison between two weak tags is false");
    }

    @Test
    @DisplayName("Weak comparison follows RFC 7232 Section 2.3.2")
    void testWeakComparison() {
        EntityTag s1 = EntityTag.strong("abc");
        EntityTag w1 = EntityTag.weak("abc");
        EntityTag w2 = EntityTag.weak("abc");
        EntityTag w3 = EntityTag.weak("def");

        assertTrue(s1.weakCompare(w1), "Weak comparison succeeds if opaque strings match");
        assertTrue(w1.weakCompare(w2));
        assertFalse(w1.weakCompare(w3));
    }
}
