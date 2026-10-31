# 🔑 JWT JWK Key Loader (`skill-jwt-jwk-key-loader`)

Zero-dependency RFC 7517 JSON Web Key (JWK) and JSON Web Key Set (JWKS) parser and public key reconstructor for Java 21.

---

## ✨ Features

- **Zero Runtime Dependencies**: No Jackson, Gson, or Nimbus JOSE required. Uses an internal lightweight RFC 8259 JSON parser and standard JCA (`java.security.*`).
- **Cryptographic Fidelity**: Reconstructs standard `RSAPublicKey` from Base64Url modulus `n` and exponent `e` with exact unsigned integer mapping.
- **JWK & JWKS Support**: Directly loads single key documents or multi-key `{"keys": [...]}` key sets keyed by `kid`.
- **Signature Ready**: The reconstructed `PublicKey` works directly with standard JCA `Signature.getInstance("SHA256withRSA")`.

---

## 🚀 Quick Start

### 1. Load an RSA JWK Public Key

```java
import com.msp.jwk.JwkLoader;
import com.msp.jwk.JwkPublicKey;
import java.security.interfaces.RSAPublicKey;

String jwkJson = """
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "auth-key-2026",
      "n": "t6X7156YUt3vg895WY65280806875213670...",
      "e": "AQAB"
    }
    """;

JwkPublicKey jwk = JwkLoader.loadJwk(jwkJson);

System.out.println("Key ID: " + jwk.kid());
System.out.println("Algorithm: " + jwk.alg());
RSAPublicKey rsaPubKey = (RSAPublicKey) jwk.publicKey();
```

### 2. Load a JWK Set (JWKS)

```java
import com.msp.jwk.JwkLoader;
import com.msp.jwk.JwkPublicKey;
import java.util.Map;

String jwksJson = """
    {
      "keys": [
        { "kty": "RSA", "kid": "primary", "n": "...", "e": "AQAB" },
        { "kty": "RSA", "kid": "secondary", "n": "...", "e": "AQAB" }
      ]
    }
    """;

Map<String, JwkPublicKey> keys = JwkLoader.loadJwks(jwksJson);
JwkPublicKey primary = keys.get("primary");
```

---

## 🛠️ Testing

```bash
./gradlew :modules:skill-jwt-jwk-key-loader:test
```
