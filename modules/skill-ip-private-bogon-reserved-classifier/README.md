# `skill-ip-private-bogon-reserved-classifier`

Zero-dependency, zero-DNS Java 21 **IPv4/IPv6 Special-Use & Bogon Classifier** with automatic **IPv4-mapped IPv6 unwrapping** and a configurable **SSRF Defense Guard**.

## Highlights
- **Zero DNS Lookups**: Pure numeric IPv4/IPv6 literal parser — never blocks on network resolution and rejects hostnames or octal/leading-zero tricks (`010.0.0.1`).
- **IPv4-Mapped IPv6 Unwrapping**: Automatically unwraps `::ffff:127.0.0.1` and hex-encoded `::ffff:a9fe:a9fe` to their underlying IPv4 addresses so SSRF filters cannot be bypassed.
- **Comprehensive Classification**: RFC 1918 private, RFC 6598 CGNAT (`100.64.0.0/10`), Loopback, Cloud Metadata IMDS (`169.254.169.254`, `fd00:ec2::254`), Link-Local, ULA (`fc00::/7`), Multicast, Broadcast, Unspecified, Documentation, Benchmarking, Reserved, and Public Unicast.
- **`SsrfGuard` Policy Engine**: Strict public-only default with customizable category rules, allow/deny CIDR overrides, and optional IPv4-mapped IPv6 blocking.

## Quick Start

```java
import com.msp.ipbogon.IpBogonClassifier;
import com.msp.ipbogon.IpClassification;
import com.msp.ipbogon.SsrfGuard;

public class Example {
    public static void main(String[] args) {
        IpClassification cls = IpBogonClassifier.classify("::ffff:169.254.169.254");
        System.out.println("Category:  " + cls.category());           // CLOUD_METADATA
        System.out.println("Unwrapped: " + cls.unwrappedFromIpv6());  // true
        System.out.println("IP:        " + cls.normalizedIp());       // 169.254.169.254

        SsrfGuard guard = SsrfGuard.strict();
        System.out.println("8.8.8.8 allowed: " + guard.check("8.8.8.8").allowed());       // true
        System.out.println("10.0.0.1 allowed: " + guard.check("10.0.0.1").allowed());     // false
    }
}
```
