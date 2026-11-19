package com.msp.ipbogon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpBogonClassifierTest {

    @Test
    void classifiesAllMajorIpv4SpecialAndPublicRanges() {
        assertEquals(IpCategory.PRIVATE_RFC1918, IpBogonClassifier.classify("10.0.0.1").category());
        assertEquals(IpCategory.PRIVATE_RFC1918, IpBogonClassifier.classify("172.16.5.4").category());
        assertEquals(IpCategory.PRIVATE_RFC1918, IpBogonClassifier.classify("192.168.1.1").category());
        assertEquals(IpCategory.CGNAT, IpBogonClassifier.classify("100.64.0.1").category());
        assertEquals(IpCategory.CGNAT, IpBogonClassifier.classify("100.127.255.254").category());
        assertEquals(IpCategory.LOOPBACK, IpBogonClassifier.classify("127.0.0.1").category());
        assertEquals(IpCategory.CLOUD_METADATA, IpBogonClassifier.classify("169.254.169.254").category());
        assertEquals(IpCategory.LINK_LOCAL, IpBogonClassifier.classify("169.254.1.10").category());
        assertEquals(IpCategory.MULTICAST, IpBogonClassifier.classify("224.0.0.251").category());
        assertEquals(IpCategory.BROADCAST, IpBogonClassifier.classify("255.255.255.255").category());
        assertEquals(IpCategory.UNSPECIFIED, IpBogonClassifier.classify("0.0.0.0").category());
        assertEquals(IpCategory.DOCUMENTATION, IpBogonClassifier.classify("192.0.2.10").category());
        assertEquals(IpCategory.DOCUMENTATION, IpBogonClassifier.classify("198.51.100.5").category());
        assertEquals(IpCategory.DOCUMENTATION, IpBogonClassifier.classify("203.0.113.9").category());
        assertEquals(IpCategory.BENCHMARKING, IpBogonClassifier.classify("198.18.0.1").category());
        assertEquals(IpCategory.RESERVED_SPECIAL, IpBogonClassifier.classify("240.0.0.1").category());

        IpClassification pub = IpBogonClassifier.classify("8.8.8.8");
        assertEquals(IpCategory.PUBLIC_UNICAST, pub.category());
        assertTrue(pub.isPublic());
        assertFalse(pub.isBogon());
        assertFalse(pub.unwrappedFromIpv6());
    }

    @Test
    void classifiesIpv6RangesAndUnwrapsIpv4MappedIpv6() {
        assertEquals(IpCategory.LOOPBACK, IpBogonClassifier.classify("::1").category());
        assertEquals(IpCategory.UNSPECIFIED, IpBogonClassifier.classify("::").category());
        assertEquals(IpCategory.LINK_LOCAL, IpBogonClassifier.classify("fe80::1%eth0").category());
        assertEquals(IpCategory.UNIQUE_LOCAL_IPV6, IpBogonClassifier.classify("fc00::1").category());
        assertEquals(IpCategory.UNIQUE_LOCAL_IPV6, IpBogonClassifier.classify("fd12:3456::1").category());
        assertEquals(IpCategory.CLOUD_METADATA, IpBogonClassifier.classify("fd00:ec2::254").category());
        assertEquals(IpCategory.MULTICAST, IpBogonClassifier.classify("ff02::1").category());
        assertEquals(IpCategory.DOCUMENTATION, IpBogonClassifier.classify("2001:db8::1").category());
        assertEquals(IpCategory.BENCHMARKING, IpBogonClassifier.classify("2001:2::1").category());
        assertEquals(IpCategory.PUBLIC_UNICAST, IpBogonClassifier.classify("2606:4700:4700::1111").category());

        // IPv4-mapped IPv6 unwrapping (both dotted and hex forms)
        IpClassification mappedLoopback = IpBogonClassifier.classify("::ffff:127.0.0.1");
        assertEquals(IpCategory.LOOPBACK, mappedLoopback.category());
        assertEquals(4, mappedLoopback.ipVersion());
        assertTrue(mappedLoopback.unwrappedFromIpv6());
        assertEquals("127.0.0.1", mappedLoopback.normalizedIp());

        IpClassification mappedMetadata = IpBogonClassifier.classify("::ffff:a9fe:a9fe");
        assertEquals(IpCategory.CLOUD_METADATA, mappedMetadata.category());
        assertTrue(mappedMetadata.unwrappedFromIpv6());
        assertEquals("169.254.169.254", mappedMetadata.normalizedIp());

        IpClassification mappedPublic = IpBogonClassifier.classify("[::ffff:1.1.1.1]");
        assertEquals(IpCategory.PUBLIC_UNICAST, mappedPublic.category());
        assertTrue(mappedPublic.unwrappedFromIpv6());
    }

    @Test
    void ssrfGuardEnforcesStrictAndCustomPolicies() {
        SsrfGuard strict = SsrfGuard.strict();
        assertTrue(strict.check("1.1.1.1").allowed());
        assertTrue(strict.check("2606:4700:4700::1111").allowed());
        assertFalse(strict.check("127.0.0.1").allowed());
        assertFalse(strict.check("::ffff:127.0.0.1").allowed());
        assertFalse(strict.check("169.254.169.254").allowed());
        assertFalse(strict.check("100.64.10.5").allowed());
        assertThrows(SecurityException.class, () -> strict.assertAllowed("10.0.0.5"));

        // Custom policy: allow internal 10.20.0.0/16 but explicitly deny 10.20.99.0/24 and block mapped IPv6
        SsrfGuard custom = SsrfGuard.builder()
                .allowCidr("10.20.0.0/16")
                .denyCidr("10.20.99.0/24")
                .blockIpv4MappedIpv6(true)
                .build();

        assertTrue(custom.check("10.20.1.5").allowed());
        assertFalse(custom.check("10.20.99.1").allowed()); // deny overrides allow
        assertFalse(custom.check("10.30.1.5").allowed());
        assertFalse(custom.check("::ffff:1.1.1.1").allowed()); // mapped IPv6 blocked
    }

    @Test
    void invalidLiteralsThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> IpBogonClassifier.classify((String) null));
        assertThrows(IllegalArgumentException.class, () -> IpBogonClassifier.classify(""));
        assertThrows(IllegalArgumentException.class, () -> IpBogonClassifier.classify("example.com"));
        assertThrows(IllegalArgumentException.class, () -> IpBogonClassifier.classify("256.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> IpBogonClassifier.classify("010.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> IpBogonClassifier.classify("1::2::3"));
    }
}
