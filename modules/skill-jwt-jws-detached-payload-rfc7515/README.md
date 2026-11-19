# skill-jwt-jws-detached-payload-rfc7515

RFC 7515 Appendix F & RFC 7797 (`b64=false`) JWS Detached Content (`<header>..<signature>`) Signer and Streaming Verifier for Open Banking and webhook APIs in pure Java 21.

## Architecture Highlights
- **Detached Content Serialization**: Produces and parses compact detached JWS tokens (`<base64url-header>..<base64url-signature>`) where the HTTP body is transmitted separately.
- **RFC 7797 Unencoded Payload Option (`"b64":false`, `"crit":["b64"]`)**: Eliminates Base64 encoding overhead for multi-megabyte HTTP streams while enforcing strict `"crit":["b64"]` validation.
- **Zero-Allocation Streaming Verification**: Streams `InputStream` payloads directly through `javax.crypto.Mac` (`HS256`, `HS384`, `HS512`) or `java.security.Signature` (`RS256`, `ES256` in IEEE P1363 format).

## Usage Example

```java
import com.msp.jwsdetached.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

byte[] secret = "webhook-shared-secret-key-32bytes".getBytes(StandardCharsets.UTF_8);
byte[] body = "{\"event\":\"payment.succeeded\"}".getBytes(StandardCharsets.UTF_8);

// Sign with RFC 7797 unencoded payload (b64=false)
DetachedJwsToken token = JwsDetachedSignerVerifier.signHmac(
        JwsAlgorithm.HS256, secret, body, false, "kid-2026", Map.of());

String xJwsSignatureHeader = token.toCompactDetached(); // eyJhbGciOiJIUzI1NiIs...
boolean valid = JwsDetachedSignerVerifier.verifyHmac(xJwsSignatureHeader, secret, body);
```
