# skill-mock-data-generator

Zero-dependency, high-performance mock data generator for realistic Korean and English identities, street addresses, phone numbers, emails, prices, dates, and deterministic seeded tests.

## Features
- **Korean & English Personas**: Full names, first names, and last names.
- **Realistic Phone Numbers**: `010-XXXX-XXXX` mobile and regional landline numbers.
- **Korean Addresses & Postal Codes**: 5-digit postal codes (01000~63999) with realistic streets and districts.
- **Seedable & Deterministic**: Supply a fixed seed for 100% reproducible test fixtures.
- **Dates, Prices & Tokens**: Range-bounded dates, step-aligned prices, UUIDv4, alphanumeric and numeric strings.
- **Zero Runtime Dependencies**: Pure Java 21 standard library.

## Quick Start

```java
import com.msp.mockdata.MockDataGenerator;

// Create deterministic generator for unit testing
MockDataGenerator mock = MockDataGenerator.deterministic(42L);

String name = mock.koreanName();           // e.g. "김민준"
String phone = mock.koreanMobilePhoneNumber(); // e.g. "010-5421-9872"
String email = mock.email();               // e.g. "james.smith42@gmail.com"
String address = mock.koreanAddress();     // e.g. "서울특별시 강남구 테헤란로 124, 502호"
long price = mock.price(1000, 50000, 500); // 1000 to 50000 in 500 increments
```
