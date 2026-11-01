package com.msp.bgp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BgpPrefixAggregatorTest {

    @Test
    @DisplayName("Empty or single prefix aggregation")
    void testEmptyAndSingle() {
        AggregationResult empty = BgpPrefixAggregator.aggregate();
        assertEquals(0, empty.originalCount());
        assertEquals(0, empty.aggregatedCount());
        assertEquals(0.0, empty.reductionPercentage());

        AggregationResult single = BgpPrefixAggregator.aggregate("192.168.1.0/24");
        assertEquals(1, single.originalCount());
        assertEquals(1, single.aggregatedCount());
        assertEquals("192.168.1.0/24", single.toCidrStrings().getFirst());
        assertEquals(0.0, single.reductionPercentage());
    }

    @Test
    @DisplayName("Merge adjacent power-of-two sibling subnets (/25 + /25 -> /24)")
    void testSiblingMerge() {
        AggregationResult result = BgpPrefixAggregator.aggregate(
                "192.0.2.0/25",
                "192.0.2.128/25"
        );

        assertEquals(2, result.originalCount());
        assertEquals(1, result.aggregatedCount());
        assertEquals("192.0.2.0/24", result.toCidrStrings().getFirst());
        assertEquals(50.0, result.reductionPercentage());
    }

    @Test
    @DisplayName("Recursive multi-tier aggregation (4 x /24 -> 1 x /22)")
    void testRecursiveMultiTierAggregation() {
        AggregationResult result = BgpPrefixAggregator.aggregate(
                "10.0.0.0/24",
                "10.0.1.0/24",
                "10.0.2.0/24",
                "10.0.3.0/24"
        );

        assertEquals(4, result.originalCount());
        assertEquals(1, result.aggregatedCount());
        assertEquals("10.0.0.0/22", result.toCidrStrings().getFirst());
        assertEquals(75.0, result.reductionPercentage());
    }

    @Test
    @DisplayName("Eliminate redundant contained subnets (supernet containment)")
    void testRedundantSubnetElimination() {
        AggregationResult result = BgpPrefixAggregator.aggregate(
                "10.0.0.0/8",
                "10.1.0.0/16",
                "10.1.2.0/24",
                "10.1.2.5/32"
        );

        assertEquals(4, result.originalCount());
        assertEquals(1, result.aggregatedCount());
        assertEquals("10.0.0.0/8", result.toCidrStrings().getFirst());
        assertEquals(75.0, result.reductionPercentage());
    }

    @Test
    @DisplayName("Disjoint non-sibling subnets cannot merge")
    void testDisjointSubnets() {
        AggregationResult result = BgpPrefixAggregator.aggregate(
                "192.168.1.0/24",
                "172.16.0.0/16",
                "10.0.0.0/24"
        );

        assertEquals(3, result.originalCount());
        assertEquals(3, result.aggregatedCount());
        assertEquals(0.0, result.reductionPercentage());
    }

    @Test
    @DisplayName("Partial merge with leftover sibling")
    void testPartialMerge() {
        // 3 of 4 subnets: 10.0.0.0/24 and 10.0.1.0/24 merge to /23, 10.0.2.0/24 remains
        AggregationResult result = BgpPrefixAggregator.aggregate(
                "10.0.0.0/24",
                "10.0.1.0/24",
                "10.0.2.0/24"
        );

        assertEquals(3, result.originalCount());
        assertEquals(2, result.aggregatedCount());
        List<String> aggregated = result.toCidrStrings();
        assertTrue(aggregated.contains("10.0.0.0/23"));
        assertTrue(aggregated.contains("10.0.2.0/24"));
    }

    @Test
    @DisplayName("IPv6 route summarization (/33 + /33 -> /32)")
    void testIpv6Aggregation() {
        AggregationResult result = BgpPrefixAggregator.aggregate(
                "2001:db8::/33",
                "2001:db8:8000::/33"
        );

        assertEquals(2, result.originalCount());
        assertEquals(1, result.aggregatedCount());
        assertEquals("2001:db8::/32", result.toCidrStrings().getFirst());
        assertEquals(50.0, result.reductionPercentage());
    }

    @Test
    @DisplayName("Mixed IPv4 and IPv6 input aggregated independently")
    void testMixedVersions() {
        AggregationResult result = BgpPrefixAggregator.aggregate(
                "192.0.2.0/25",
                "192.0.2.128/25",
                "2001:db8::/33",
                "2001:db8:8000::/33"
        );

        assertEquals(4, result.originalCount());
        assertEquals(2, result.aggregatedCount());
        List<String> list = result.toCidrStrings();
        assertTrue(list.contains("192.0.2.0/24"));
        assertTrue(list.contains("2001:db8::/32"));
    }

    @Test
    @DisplayName("Non-aligned network address is automatically normalized to network boundary")
    void testNetworkAddressNormalization() {
        CidrPrefix prefix = CidrPrefix.parse("192.168.1.135/24");
        assertEquals("192.168.1.0/24", prefix.toCidrString());
    }
}
