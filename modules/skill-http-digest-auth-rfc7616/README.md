# skill-http-digest-auth-rfc7616

Zero-dependency Java 21 implementation of **RFC 7616 HTTP Digest Access Authentication**.

## Architecture Highlights
- **Algorithms**: `SHA-256`, `SHA-256-sess`, `MD5`, `MD5-sess`
- **QoP Modes**: `auth`, `auth-int` (with entity body hashing), and legacy RFC 2069 (`NONE`)
- **RFC 7616 Features**: `userhash=true`, `stale`, `opaque`, quoted-string escaping/unescaping
- **Security**: Constant-time response verification (`MessageDigest.isEqual`) and thread-safe `NonceTracker` with monotonic 8-hex-digit `nc` replay protection & TTL eviction

## Usage Example
```java
import com.msp.digestauth.*;
import java.time.Duration;

// 1. Server issues challenge
NonceTracker tracker = new NonceTracker();
String nonce = tracker.issueNonce(Duration.ofMinutes(5));
DigestChallenge challenge = DigestChallenge.builder("api.example.org", nonce)
        .algorithm(DigestAlgorithm.SHA_256)
        .qopOptions(Qop.AUTH)
        .build();
String wwwAuthHeader = challenge.toHeaderValue();

// 2. Client parses challenge and generates Authorization header
DigestChallenge parsed = DigestChallenge.parse(wwwAuthHeader);
DigestCredentials creds = DigestAuthenticator.authorize(
        parsed, "alice", "s3cr3t", "GET", "/v1/orders", "client-cnonce-01", 1);
String authHeader = creds.toHeaderValue();

// 3. Server verifies digest and replay protection
DigestCredentials received = DigestCredentials.parse(authHeader);
boolean digestOk = DigestAuthenticator.verify(
        received, "api.example.org", "alice", "s3cr3t", "GET", "/v1/orders");
ReplayCheckResult replay = tracker.validateAndAdvance(
        received.nonce(), received.nc().orElse(""));
```
