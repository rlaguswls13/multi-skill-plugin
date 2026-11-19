# 🌐 starter-http-network

> **HTTP Protocol (RFC 7807 / CORS / ETag / RFC 8288 Link Header) & IP Network (Bogon/SSRF Guard / CIDR) Starter Bundle**

Zero-dependency Java 21 domain starter aggregating 6 single-purpose HTTP and networking modules behind the unified `HttpNetworkKit` facade.

## Included Modules
- `skill-http-rfc7807-problem-details` — RFC 7807 / RFC 9457 `application/problem+json` builder & codec
- `skill-http-cors-preflight-evaluator` — W3C Fetch/CORS preflight and actual request evaluator
- `skill-http-etag-conditional-evaluator` — RFC 7232 ETag (`If-Match`, `If-None-Match`, `If-Modified-Since`, `If-Unmodified-Since`) engine
- `skill-http-link-header-rfc8288-parser` — RFC 8288 `Link` header parser, formatter, and pagination helper
- `skill-ip-private-bogon-reserved-classifier` — Zero-DNS IPv4/IPv6 Bogon classifier & SSRF policy guard
- `skill-ip-subnet-cidr-calculator` — Bitwise IPv4 CIDR subnet analyzer, containment, and overlap checker

## Gradle Dependency
```groovy
dependencies {
    implementation 'com.msp:starter-http-network:2.0.0'
}
```

### JitPack
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.rlaguswls13.multi-skill-plugin:starter-http-network:2.0.0'
}
```

## Quick Start (`HttpNetworkKit`)
```java
import com.msp.cors.CorsConfig;
import com.msp.starter.httpnet.HttpNetworkKit;

import java.util.Map;

// 1. RFC 7807 Problem Details JSON
String problemJson = HttpNetworkKit.problemDetailsJson(404, "Not Found", "Order #42 not found");

// 2. W3C CORS Preflight Evaluation
CorsConfig cors = CorsConfig.builder()
        .allowOrigin("https://app.example.com")
        .allowMethods("GET", "POST")
        .allowHeaders("Authorization")
        .build();
var corsResult = HttpNetworkKit.evaluateCorsPreflight(cors, "https://app.example.com", "POST", "Authorization");

// 3. RFC 7232 ETag Conditional Check
var cond = HttpNetworkKit.evaluateEtag("\"v1\"", Map.of("If-None-Match", "\"v1\""), "GET"); // 304 Not Modified

// 4. RFC 8288 Link Pagination Header
String linkHeader = HttpNetworkKit.buildPaginationLinkHeader(
        "https://api.example.com/items?page=1", null, "https://api.example.com/items?page=2", null);
var pagination = HttpNetworkKit.parsePaginationLinks(linkHeader);

// 5. SSRF Guard & IP Bogon Check
boolean safePublic = HttpNetworkKit.checkSsrfStrict("169.254.169.254").allowed(); // false (CLOUD_METADATA)

// 6. IPv4 CIDR Subnet Calculation
var subnet = HttpNetworkKit.calculateSubnet("192.168.1.0/24");
boolean inSubnet = HttpNetworkKit.cidrContains("192.168.1.0/24", "192.168.1.42");
```
