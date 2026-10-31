# URL Canonicalizer & Cleaner (`skill-url-canonicalizer-cleaner`)

Lightweight, high-performance canonical URL generator that removes marketing/analytics tracking tokens (`utm_*`, `fbclid`, `gclid`), strips redundant default ports (`:80`, `:443`, `:21`), collapses duplicate slashes, normalizes dot segments (`.`, `..`), and sorts query parameters alphabetically.

## Features
- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Comprehensive Tracking Removal**: Automatically strips `utm_*`, `fbclid`, `gclid`, `_ga`, `_gl`, `mc_cid`, `yclid`, `msclkid`, and supports custom tracking parameter extensions.
- **Path Normalization**: Safely collapses duplicate slashes (`//` -> `/`) and resolves relative dot segments (`.` and `..`).
- **Default Port Stripping**: Removes standard default ports (`http:80`, `https:443`, `ftp:21`) while keeping custom ports intact.
- **Query Parameter Ordering**: Deterministically sorts query parameters alphabetically for consistent caching and deduplication.
- **Fine-Grained Utilities**: Provides individual helpers (`removeTrackingParams`, `normalizePath`, `sortQueryParams`) alongside all-in-one `canonicalize(url)`.

## Usage Examples

### 1. Full URL Canonicalization
```java
import com.msp.urlcanon.UrlCanonicalizer;

String dirtyUrl = "HTTPS://Example.COM:443/docs//api/./v1/../v2/?utm_source=twitter&version=2&format=json&fbclid=xyz#section";

String canonical = UrlCanonicalizer.canonicalize(dirtyUrl);
// Output: "https://example.com/docs/api/v2/?format=json&version=2"
```

### 2. Standalone Helpers
```java
// 1. Remove tracking parameters only
String cleanedQuery = UrlCanonicalizer.removeTrackingParams("https://shop.com/item?id=99&utm_campaign=sale&gclid=abc");
// Output: "https://shop.com/item?id=99"

// 2. Normalize path only
String cleanPath = UrlCanonicalizer.normalizePath("https://api.com/v1//users/./list/../all/");
// Output: "https://api.com/v1/users/all/"

// 3. Sort query parameters only
String sorted = UrlCanonicalizer.sortQueryParams("https://site.com/search?z=3&a=1&m=2");
// Output: "https://site.com/search?a=1&m=2&z=3"
```

### 3. Custom Options
```java
import com.msp.urlcanon.CanonicalizeOptions;

CanonicalizeOptions options = CanonicalizeOptions.builder()
        .stripFragment(false) // Retain anchor fragments (#anchor)
        .addExtraTrackingParam("custom_campaign_id")
        .build();

String result = UrlCanonicalizer.canonicalize(dirtyUrl, options);
```
