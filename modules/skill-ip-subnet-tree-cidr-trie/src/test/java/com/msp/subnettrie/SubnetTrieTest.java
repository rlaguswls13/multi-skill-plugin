package com.msp.subnettrie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Subnet Trie CIDR Prefix Search Tests")
class SubnetTrieTest {

    @Test
    @DisplayName("IPv4 Longest Prefix Match (LPM) finds most specific subnet")
    void testIpv4LongestPrefixMatch() {
        SubnetTrie<String> trie = new SubnetTrie<>();
        trie.insert("10.0.0.0/8", "Corporate-A");
        trie.insert("192.168.0.0/16", "Private-B");
        trie.insert("192.168.1.0/24", "Branch-Office");
        trie.insert("192.168.1.50/32", "Printer-Host");

        assertEquals(4, trie.size());

        // Exact host IP
        assertEquals(Optional.of("Printer-Host"), trie.findLongestMatchingSubnet("192.168.1.50"));

        // Host in Branch Office /24
        assertEquals(Optional.of("Branch-Office"), trie.findLongestMatchingSubnet("192.168.1.100"));

        // Host in Private-B /16
        assertEquals(Optional.of("Private-B"), trie.findLongestMatchingSubnet("192.168.2.1"));

        // Host in Corporate-A /8
        assertEquals(Optional.of("Corporate-A"), trie.findLongestMatchingSubnet("10.50.2.1"));

        // External un-routed IP
        assertEquals(Optional.empty(), trie.findLongestMatchingSubnet("8.8.8.8"));
        assertTrue(trie.containsIp("192.168.1.1"));
        assertFalse(trie.containsIp("8.8.4.4"));
    }

    @Test
    @DisplayName("IPv6 LPM correctly traverses 128-bit address depth")
    void testIpv6Subnets() {
        SubnetTrie<String> trie = new SubnetTrie<>();
        trie.insert("2001:db8::/32", "Doc-Net-V6");
        trie.insert("2001:db8:abcd::/48", "DataCenter-V6");
        trie.insert("::1", "Loopback-V6");

        assertEquals(Optional.of("DataCenter-V6"), trie.findLongestMatchingSubnet("2001:db8:abcd:0001::1"));
        assertEquals(Optional.of("Doc-Net-V6"), trie.findLongestMatchingSubnet("2001:db8:ffff::1"));
        assertEquals(Optional.of("Loopback-V6"), trie.findLongestMatchingSubnet("::1"));
        assertEquals(Optional.empty(), trie.findLongestMatchingSubnet("fe80::1"));
    }

    @Test
    @DisplayName("Overlapping subnets find ancestors, exact, and subtree descendants")
    void testOverlappingSubnets() {
        SubnetTrie<String> trie = new SubnetTrie<>();
        trie.insert("10.0.0.0/8", "Net-10");
        trie.insert("10.1.0.0/16", "Net-10-1");
        trie.insert("10.1.2.0/24", "Net-10-1-2");
        trie.insert("172.16.0.0/12", "Net-172");

        // Query overlapping with 10.1.0.0/16
        // Ancestor: 10.0.0.0/8, Exact: 10.1.0.0/16, Descendant: 10.1.2.0/24
        List<String> overlaps = trie.findOverlappingSubnets("10.1.0.0/16");
        assertEquals(3, overlaps.size());
        assertTrue(overlaps.contains("10.0.0.0/8"));
        assertTrue(overlaps.contains("10.1.0.0/16"));
        assertTrue(overlaps.contains("10.1.2.0/24"));
        assertFalse(overlaps.contains("172.16.0.0/12"));

        // Query disjoint subnet
        List<String> disjoint = trie.findOverlappingSubnets("192.168.0.0/16");
        assertTrue(disjoint.isEmpty());
    }

    @Test
    @DisplayName("Find all matching enclosing subnets returns ordered hierarchy")
    void testFindAllMatchingSubnets() {
        SubnetTrie<String> trie = new SubnetTrie<>();
        trie.insert("10.0.0.0/8", "Root-10");
        trie.insert("10.1.0.0/16", "Mid-10.1");
        trie.insert("10.1.1.0/24", "Leaf-10.1.1");

        List<String> matches = trie.findAllMatchingSubnets("10.1.1.99");
        assertEquals(3, matches.size());
        assertEquals("Root-10", matches.get(0));
        assertEquals("Mid-10.1", matches.get(1));
        assertEquals("Leaf-10.1.1", matches.get(2));
    }

    @Test
    @DisplayName("Removal cleans up node and prunes dead branches")
    void testRemovalAndPruning() {
        SubnetTrie<String> trie = new SubnetTrie<>();
        trie.insert("192.168.1.0/24", "Subnet-A");
        trie.insert("192.168.0.0/16", "Subnet-B");

        assertEquals(2, trie.size());
        assertEquals(Optional.of("Subnet-A"), trie.findExact("192.168.1.0/24"));

        boolean removed = trie.remove("192.168.1.0/24");
        assertTrue(removed);
        assertEquals(1, trie.size());
        assertEquals(Optional.empty(), trie.findExact("192.168.1.0/24"));

        // LPM now falls back to enclosing /16
        assertEquals(Optional.of("Subnet-B"), trie.findLongestMatchingSubnet("192.168.1.50"));
    }
}
