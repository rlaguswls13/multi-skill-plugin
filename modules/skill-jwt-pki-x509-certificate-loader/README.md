# X.509 Certificate & PKI Loader (`skill-jwt-pki-x509-certificate-loader`)

Zero-dependency Java 21 library for parsing, loading, inspecting, and validating X.509 certificates and certificate chains for JWT (e.g. `x5c`), TLS, and enterprise PKI.

## Features

- **Format Agnostic**: Seamlessly loads PEM (single or chained `-----BEGIN CERTIFICATE-----`), binary DER bytes, and streams.
- **Deep Inspection**: Extracts Subject DN, Issuer DN, Serial Number (Hex/Decimal), validity period, Signature Algorithm, Public Key (RSA bit size, EC curve), KeyUsage flags, ExtendedKeyUsage OIDs, and SAN (DNS, IP, URI, Email).
- **Chain Verification**: Validates certificate chains against trust anchor public keys, checking signature integrity and validity timestamps.
- **In-Memory Self-Signed Generator**: Built-in pure Java DER encoder to generate valid test certificates on the fly without OpenSSL or external tools.

## Runnable Code Example

```java
import com.msp.x509.ParsedCertificate;
import com.msp.x509.ValidationResult;
import com.msp.x509.X509CertificateLoader;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

public class X509Example {
    public static void main(String[] args) throws Exception {
        // 1. Generate in-memory self-signed certificate
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X509Certificate cert = X509CertificateLoader.createSelfSignedRsaCertificate(
                kp, "CN=api.service.internal, O=MSP Corp", 365
        );

        // 2. Parse and inspect certificate metadata
        ParsedCertificate parsed = X509CertificateLoader.parse(cert);
        System.out.println("Subject DN: " + parsed.subjectDn());
        System.out.println("Key Size: " + parsed.keySizeBits() + " bits");
        System.out.println("Valid now: " + parsed.isValidAt(Instant.now()));

        // 3. Export to PEM format
        String pem = X509CertificateLoader.toPem(cert);
        System.out.println(pem);

        // 4. Validate Chain
        ValidationResult result = X509CertificateLoader.verifyChain(
                List.of(cert), kp.getPublic(), Instant.now()
        );
        System.out.println("Chain valid: " + result.valid());
    }
}
```
