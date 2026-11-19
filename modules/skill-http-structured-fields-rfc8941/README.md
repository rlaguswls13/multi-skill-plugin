# skill-http-structured-fields-rfc8941

RFC 8941 / RFC 9651 HTTP Structured Field Values (`Item`, `List`, `Dictionary`, `InnerList`, `Parameters`) strict parser and canonical serializer in pure Java 21.

## Architecture Highlights
- **Zero Dependencies**: Built strictly with Java 21 sealed interfaces (`BareItem`, `SfMember`) and immutable records (`SfItem`, `SfInnerList`, `SfList`, `SfDictionary`).
- **RFC 8941 & RFC 9651 Compliant**:
  - **Bare Items**: 15-digit signed `Integer`, 12.3-digit `Decimal` (`BigDecimal` with HALF_EVEN rounding), printable ASCII `String` (`\"` and `\\` escaping), `Token`, Base64 `Byte Sequence` (`:...:`), and `Boolean` (`?1`, `?0`).
  - **Parameters & Dictionary Omission Rules**: Implicit boolean `?1` parameter values (`;flag`) and dictionary entries (`key;param=1`) are parsed and canonically serialized without redundant `=?1`.

## Usage Example

```java
import com.msp.sfv.*;

// Parse an RFC 9218 Priority header (Dictionary)
SfDictionary priority = StructuredFields.parseDictionary("u=3, i");
System.out.println(StructuredFields.serialize(priority)); // "u=3, i"

// Parse a List with an Inner List and Parameters
SfList accept = StructuredFields.parseList("sugar, (\"milk\" \"honey\";organic);source=local");
System.out.println(StructuredFields.serialize(accept));
```
