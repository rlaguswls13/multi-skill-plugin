# 📋 skill-json-schema-lite-validator

> **Ultra-lightweight schema validator for Map/JSON structures without external dependencies**  
> Pure Java 21 zero-dependency validator for parsed JSON and Map structures with type checking, range validation, regex matching, and nested array sizing.

---

## 🚀 Key Features

- **Zero External Dependencies**: Pure standard Java 21 library (`java.util`, `java.util.regex`).
- **Flexible Type Checking**: Supports `STRING`, `NUMBER`, `BOOLEAN`, `ARRAY`, `OBJECT`, and `ANY`.
- **Comprehensive Constraints**:
  - `required` / `optional` presence and nullability
  - `minLength` / `maxLength` for strings and arrays
  - `min` / `max` numerical boundaries
  - Regex `pattern` matching
- **Immutable Validation Result**: `ValidationResult` record detailing validation outcome and human-readable error messages.
- **Thread-Safe**: Concurrent execution and thread-safe dynamic field definition.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-json-schema-lite-validator:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Basic Schema Definition and Validation

```java
import com.msp.jsonschema.*;
import java.util.Map;

JsonSchemaValidator validator = new JsonSchemaValidator()
        .defineField("username", FieldConstraint.builder()
                .required(true)
                .type(JsonFieldType.STRING)
                .minLength(3)
                .maxLength(20)
                .pattern("^[a-zA-Z0-9_]+$")
                .build())
        .defineField("age", FieldConstraint.builder()
                .required(true)
                .type(JsonFieldType.NUMBER)
                .min(18)
                .max(100)
                .build())
        .defineField("active", FieldConstraint.required(JsonFieldType.BOOLEAN))
        .defineField("tags", FieldConstraint.builder()
                .type(JsonFieldType.ARRAY)
                .minLength(1)
                .maxLength(10)
                .build());

Map<String, Object> payload = Map.of(
        "username", "user_admin",
        "age", 28,
        "active", true,
        "tags", List.of("admin", "ops")
);

ValidationResult result = validator.validate(payload);
if (result.isValid()) {
    System.out.println("Payload is valid!");
} else {
    System.err.println("Validation failed with errors:");
    result.errorMessages().forEach(System.err::println);
}
```

### 2. Optional Fields and Convenience Builders

```java
JsonSchemaValidator profileValidator = new JsonSchemaValidator()
        .defineField("id", FieldConstraint.required(JsonFieldType.STRING))
        .defineField("nickname", FieldConstraint.optional(JsonFieldType.STRING))
        .defineField("metadata", FieldConstraint.optional(JsonFieldType.OBJECT));
```
