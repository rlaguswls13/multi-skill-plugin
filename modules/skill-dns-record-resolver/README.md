# 🌐 DNS Record Resolver (`skill-dns-record-resolver`)

A lightweight, zero-dependency DNS resolution module for Java 21 utilizing the standard JDK JNDI DNS Service Provider Interface (`com.sun.jndi.dns.DnsContextFactory`).

---

## ✨ Features

- **Zero Runtime Dependencies**: Pure Java standard library (`javax.naming.*`, `java.net.*`).
- **Comprehensive Record Types**: Supports `A`, `AAAA`, `MX`, `TXT`, `CNAME`, `NS`, and `PTR`.
- **Fail-Safe Operation**: Captures resolution failures, timeouts, and unreachable servers without unchecked crashes or thrown exceptions.
- **Localhost & LAN Fallback**: Gracefully falls back to JDK `InetAddress` for local network host resolution when JNDI lookup fails.
- **Custom Upstream DNS**: Optionally specify custom DNS servers (e.g. `8.8.8.8`, `1.1.1.1`) and custom timeouts.

---

## 🚀 Quick Start

### 1. Simple IPv4 Resolution

```java
import com.msp.dns.DnsRecordResolver;
import java.util.List;

DnsRecordResolver resolver = new DnsRecordResolver();
List<String> ipv4Addresses = resolver.resolveA("localhost");
System.out.println("IPv4 Addresses: " + ipv4Addresses);
```

### 2. General Record Resolution (TXT, MX, AAAA, etc.)

```java
import com.msp.dns.DnsRecordResolver;
import com.msp.dns.DnsRecordType;
import com.msp.dns.DnsLookupResult;

DnsRecordResolver resolver = new DnsRecordResolver("8.8.8.8");
DnsLookupResult result = resolver.resolve("example.com", DnsRecordType.TXT);

if (result.success()) {
    System.out.println("Query took: " + result.elapsedMillis() + " ms");
    result.records().forEach(record -> {
        System.out.println(record.type() + " -> " + record.value());
    });
} else {
    System.err.println("Lookup failed: " + result.errorMessage());
}
```

---

## 🛠️ Testing

```bash
./gradlew :modules:skill-dns-record-resolver:test
```
