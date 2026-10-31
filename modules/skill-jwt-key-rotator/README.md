# JWT Key Rotator (`skill-jwt-key-rotator`)

High-reliability, zero-dependency cryptographic secret key rotation manager with **grace period verification** and automatic dual-key swapping for JWT (HMAC/AES) tokens.

## Features
- **Zero Runtime Dependencies**: Pure Java 21 standard library (`java.security.SecureRandom`, `java.time`).
- **Grace Period Support**: When a key rotates, existing issued tokens can still be verified safely until the grace period expires.
- **Active / Validation Key Separation**:
  - `getActiveKey()`: Current key for issuing and signing new tokens.
  - `getKey(kid)`: Verification lookup by Key ID, accepting active and grace period keys.
- **Thread-Safe**: Safe for high concurrency and virtual thread workloads.
- **Deterministic Testing**: Pluggable `java.time.Clock` enables testing key expiration without `Thread.sleep`.

## Usage Examples

### 1. Basic Key Rotation & Lookup
```java
import com.msp.keyrotator.JwtKeyRotator;
import com.msp.keyrotator.RotatedKey;
import java.time.Duration;

JwtKeyRotator rotator = JwtKeyRotator.builder()
        .rotationInterval(Duration.ofDays(1))   // Recommended rotation cadence
        .gracePeriod(Duration.ofHours(6))       // Grace period for verification of previous keys
        .build();

// 1. Issue JWT with current active key
RotatedKey activeKey = rotator.getActiveKey();
String kid = activeKey.kid();
byte[] secretBytes = activeKey.secretKey();

// 2. Rotate to a new key (e.g. daily cron job or trigger)
RotatedKey newActiveKey = rotator.rotateKey();

// 3. Verify incoming JWT: old token with 'kid' is still valid!
rotator.getKey(kid).ifPresent(key -> {
    // Validate signature
});
```

### 2. Auto Rotation on Access
```java
JwtKeyRotator rotator = JwtKeyRotator.builder()
        .rotationInterval(Duration.ofHours(12))
        .gracePeriod(Duration.ofHours(3))
        .autoRotateOnAccess(true) // Automatically rotates when interval elapses
        .build();

RotatedKey key = rotator.getActiveKey(); // Auto-rotates if older than 12 hours
```
