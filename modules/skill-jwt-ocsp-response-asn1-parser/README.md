# skill-jwt-ocsp-response-asn1-parser

Zero-dependency Java 21 **RFC 6960 X.509 OCSP Response ASN.1 DER Parser & Builder**.

## Architecture Highlights
- **Pure-Java ASN.1 DER Codec**: Strict DER definite-length decoding, OID encoding/decoding, UTC `GeneralizedTime` parsing, and raw TBS slice capture (`rawTbsBytes()`) for downstream signature verification
- **Complete RFC 6960 Model**:
  - `OcspResponseStatus` (`SUCCESSFUL`, `MALFORMED_REQUEST`, `INTERNAL_ERROR`, `TRY_LATER`, `SIG_REQUIRED`, `UNAUTHORIZED`)
  - `BasicOcspResponse`, `ResponseData`, `ResponderId` (`ByName` / `ByKey`)
  - `SingleResponse` with `CertId`, `CertStatus` (`Good`, `Revoked` with `revocationTime` & `RevocationReason`, `Unknown`), `thisUpdate`, and optional `nextUpdate`
- **Fluent Test/Mock Builder**: `OcspResponseBuilder` constructs spec-compliant DER payloads

## Usage Example
```java
import com.msp.ocsp.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

byte[] der = OcspResponseBuilder.builder()
        .status(OcspResponseStatus.SUCCESSFUL)
        .responderId(new ResponderId.ByKey(new byte[]{1, 2, 3, 4}))
        .producedAt(Instant.parse("2026-11-19T10:00:00Z"))
        .addSingleResponse(new SingleResponse(
                new CertId(OcspResponseBuilder.OID_SHA256, new byte[]{1}, new byte[]{2}, BigInteger.TEN),
                new CertStatus.Good(),
                Instant.parse("2026-11-19T09:00:00Z"),
                Optional.of(Instant.parse("2026-11-20T09:00:00Z")),
                false))
        .buildDer();

OcspResponse response = OcspResponseParser.parse(der);
SingleResponse first = response.basicResponse().orElseThrow().tbsResponseData().responses().get(0);
switch (first.certStatus()) {
    case CertStatus.Good g -> System.out.println("Certificate is GOOD");
    case CertStatus.Revoked r -> System.out.println("REVOKED at " + r.revocationTime());
    case CertStatus.Unknown u -> System.out.println("Status UNKNOWN");
}
```
