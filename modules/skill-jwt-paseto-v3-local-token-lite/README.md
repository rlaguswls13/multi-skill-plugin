# `skill-jwt-paseto-v3-local-token-lite`

Zero-dependency Java 21 **PASETO v3.local** authenticated symmetric encryption token engine built strictly on standard JCA primitives (`HmacSHA384`, `AES/CTR/NoPadding`, `SecureRandom`).

## Highlights
- **PASETO v3.local Spec Compliant**:
  - Strict `v3.local.` header enforcement.
  - **PAE (Pre-Authentication Encoding)** over `(header, nonce, ciphertext, footer, implicitAssertion)`.
  - **HKDF-SHA384** key splitting deriving 32-byte encryption key `Ek`, 16-byte counter nonce `n2`, and 48-byte authentication key `Ak`.
  - **AES-256-CTR** encryption + **HMAC-SHA384** authentication with constant-time tag verification (`MessageDigest.isEqual`) before decryption.
- **Footer & Implicit Assertion**: Supports authenticated plaintext footers (with `extractUnverifiedFooter` for key-ID lookup) and out-of-band implicit assertions.
- **Strict Base64url**: Rejects padding (`=`) and non-canonical encodings.

## Quick Start

```java
import com.msp.paseto.PasetoV3Local;
import com.msp.paseto.PasetoV3Token;

import java.nio.charset.StandardCharsets;

public class Example {
    public static void main(String[] args) {
        byte[] key = PasetoV3Local.generateKey();

        String token = PasetoV3Local.encryptString(
                key,
                "{\"sub\":\"user-123\",\"scope\":\"read:orders\"}",
                "{\"kid\":\"key-2026-01\"}",
                "service:billing"
        );
        System.out.println("Token: " + token);

        PasetoV3Token parsed = PasetoV3Local.decrypt(
                key,
                token,
                "service:billing".getBytes(StandardCharsets.UTF_8)
        );
        System.out.println("Payload: " + parsed.payloadAsUtf8());
        System.out.println("Footer:  " + parsed.footerAsUtf8());
    }
}
```
