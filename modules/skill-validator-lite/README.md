# 🛡️ skill-validator-lite

Fluent parameter and DTO validator (email, URL, IPv4/IPv6, regex, range, notBlank) with zero runtime dependencies.

## ✨ Features

- **Zero Runtime Dependencies**: 100% Pure Java 21 standard library.
- **Fluent Builder API**: Chainable property validations with field names, typed getters, and friendly error messages.
- **Built-in Rule Set**:
  - `notBlank`, `notEmpty`, `notNull`
  - `email`, `url`, `ipv4`, `ipv6`
  - `range`, `min`, `max`, `length`
  - `pattern` (Regex / Pattern)
  - Custom rules & predicates (`check`, `field`, `rule`)
- **ValidationResult**: Thread-safe result containing detailed `ValidationError` records (`field`, `message`, `rejectedValue`).

## 🚀 Usage

### 1. DTO Validation

```java
import com.msp.validator.Validator;
import com.msp.validator.ValidationResult;

record SignUpRequest(String username, String email, String website, int age) {}

Validator<SignUpRequest> validator = Validator.<SignUpRequest>builder()
        .notBlank("username", SignUpRequest::username, "Username must not be blank")
        .email("email", SignUpRequest::email, "Invalid email address format")
        .url("website", SignUpRequest::website, "Invalid website URL")
        .range("age", SignUpRequest::age, 18, 120, "Age must be between 18 and 120")
        .build();

SignUpRequest request = new SignUpRequest("alice", "alice@example.com", "https://alice.dev", 24);
ValidationResult result = validator.validate(request);

if (result.isValid()) {
    System.out.println("Valid request!");
} else {
    result.errors().forEach(err -> 
        System.err.printf("[%s] %s (rejected: %s)%n", err.field(), err.message(), err.rejectedValue())
    );
}
```

### 2. Standalone Utility Validation

```java
import com.msp.validator.Validators;

Validators.isEmail("user@example.com"); // true
Validators.isUrl("https://github.com");  // true
Validators.isIpv4("192.168.0.1");       // true
Validators.isIpv6("2001:db8::1");       // true
Validators.isBlank("   ");              // true
```
