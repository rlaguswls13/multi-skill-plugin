# X.509 CRL Parser & Revocation Checker (`skill-jwt-revocation-crl-parser`)

Pure Java 21 standard library implementation for parsing RFC 5280 X.509 Certificate Revocation Lists (CRL) in binary DER or PEM format and performing real-time revocation and expiry checks.

## Features

- **Pure Java 21 Standard Library**: Built on `java.security.cert.CertificateFactory`, `X509CRL`, `X509CRLEntry`, and `CRLReason`. Zero 3rd party runtime dependencies.
- **Dual Format Ingestion**: Automatically detects and parses both PEM-encoded (`-----BEGIN X509 CRL-----`) and binary DER formats from byte arrays or input streams.
- **Rich Revocation Metadata**: Extracts Issuer DN, validity timestamps (`thisUpdate`, `nextUpdate`), signature algorithms, serial numbers (BigInteger and Hex), revocation timestamps, and standard RFC 5280 CRLReason codes (e.g. `KEY_COMPROMISE`, `SUPERSEDED`, `CA_COMPROMISE`).
- **High-Performance Lookups**: Provides $O(1)$ in-memory revocation query methods (`isRevoked`, `getRevocationInfo`) by `BigInteger` or hex string.
- **Expiry Validation**: Built-in verification against current or specified timestamps (`isExpired`).

## Runnable Code Example

```java
import com.msp.crl.CrlParser;
import com.msp.crl.CrlSnapshot;
import com.msp.crl.RevocationInfo;

import java.math.BigInteger;
import java.util.Optional;

public class CrlExample {
    public static void main(String[] args) {
        String pemCrl = """
                -----BEGIN X509 CRL-----
                MIIBmzCBvAIBATANBgkqhkiG9w0BAQsFADAeMRwwGgYDVQQDDBNFeGFtcGxlIENB
                ...
                -----END X509 CRL-----
                """;

        // 1. Parse PEM or DER CRL
        CrlSnapshot snapshot = CrlParser.parsePem(pemCrl);

        System.out.println("Issuer: " + snapshot.getIssuerDN());
        System.out.println("This Update: " + snapshot.getThisUpdate());
        System.out.println("Next Update: " + snapshot.getNextUpdate());
        System.out.println("Is Expired: " + snapshot.isExpired());
        System.out.println("Revoked Count: " + snapshot.revokedCount());

        // 2. Query certificate revocation status
        BigInteger serial = new BigInteger("123456789");
        boolean revoked = snapshot.isRevoked(serial);
        System.out.println("Serial 123456789 is revoked: " + revoked);

        // 3. Inspect detailed revocation reason
        Optional<RevocationInfo> info = snapshot.getRevocationInfo("A5F12");
        info.ifPresent(r -> {
            System.out.println("Revocation Date: " + r.revocationDate());
            System.out.println("Reason: " + r.reason());
        });
    }
}
```
