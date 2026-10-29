# 📞 skill-phone-validator

Phone number format validation, country code detection, and E.164 normalization with zero runtime dependencies.

## ✨ Features

- **Zero Runtime Dependencies**: 100% Pure Java 21 standard library.
- **Korean Telephone Ecosystem Support**:
  - Mobile numbers (`010`, `011`, `016`, `017`, `018`, `019`)
  - Seoul & regional landlines (`02`, `031` ~ `064`)
  - VoIP & internet phones (`070`)
  - Toll-free & virtual numbers (`080`, `050x`)
  - Corporate / representative numbers (`1588`, `1577`, `1544`, `1688`, etc.)
- **International E.164 Support**:
  - Global standard normalization (`+821012345678`, `+14155552671`)
  - Automatic dial code detection (`CountryCode`)
- **Dashed Formatter**:
  - Formats raw digits into proper regional dashes (e.g. `02-1234-5678`, `010-1234-5678`, `1588-1234`).

## 🚀 Usage

```java
import com.msp.phone.PhoneNumberValidator;
import com.msp.phone.E164Formatter;
import com.msp.phone.CountryCode;
import com.msp.phone.PhoneNumber;

// 1. Quick validation
boolean isMobile = PhoneNumberValidator.isValidKoreanMobile("010-1234-5678"); // true
boolean isE164 = PhoneNumberValidator.isValidE164("+14155552671");           // true

// 2. Normalization to E.164
String e164 = E164Formatter.toE164("010-1234-5678", CountryCode.KR); 
// "+821012345678"

// 3. Formatting
String dashed = E164Formatter.formatKorean("0212345678"); 
// "02-1234-5678"

// 4. Parsing into rich record
PhoneNumber parsed = PhoneNumberValidator.parse("01012345678", CountryCode.KR);
System.out.println("E.164: " + parsed.e164());         // +821012345678
System.out.println("Formatted: " + parsed.formatted()); // 010-1234-5678
System.out.println("Type: " + parsed.type());           // MOBILE
```
