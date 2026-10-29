# 💳 skill-creditcard-validator

Luhn algorithm checksum credit card validator and brand issuer identifier with zero runtime dependencies.

## ✨ Features

- **Zero Runtime Dependencies**: 100% Pure Java 21 standard library.
- **ISO/IEC 7812 Luhn Algorithm**: Checksum validation (`LuhnChecksum.validate`) and check digit generation (`LuhnChecksum.calculateCheckDigit`).
- **Brand Detection**: Accurately detects `VISA`, `MASTERCARD`, `AMEX`, `JCB`, `DISCOVER`, `UNIONPAY`, and `DINERS`.
- **PCI-DSS Masking**: Clean masking (`4111********1111`) and grouped dashed formatting (`4111-****-****-1111`, `3782-******-10005`).

## 🚀 Usage

```java
import com.msp.card.CreditCardValidator;
import com.msp.card.CardValidationResult;
import com.msp.card.CardMasker;
import com.msp.card.CardBrand;

// 1. Validate card number
CardValidationResult result = CreditCardValidator.validate("4111-1111-1111-1111");

if (result.isValid()) {
    System.out.println("Brand: " + result.brand().getDisplayName()); // Visa
    System.out.println("Masked: " + CardMasker.maskDashed(result.cleanNumber())); // 4111-****-****-1111
} else {
    System.err.println("Validation error: " + result.errorMessage());
}

// 2. Generate Luhn check digit
int checkDigit = LuhnChecksum.calculateCheckDigit("411111111111111"); // 1
```
