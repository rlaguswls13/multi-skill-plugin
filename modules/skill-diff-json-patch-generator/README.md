# 🧩 skill-diff-json-patch-generator

> RFC 6902 JSON Patch diff-and-patch engine with zero 3rd-party dependencies for pure Java 21.

---

## 🌟 Key Features

- **RFC 6902 Compliant**: Generates standardized JSON Patch operations (`add`, `remove`, `replace`).
- **RFC 6901 JSON Pointer**: Safely escapes JSON pointer segments (`~` as `~0`, `/` as `~1`).
- **Deep Structural Comparison**: Handles arbitrarily nested objects and arrays.
- **Index-Safe Array Removal**: Removes array elements in reverse index order to prevent index drift during patch application.
- **Pure Java 21 Parser**: Built-in recursive-descent JSON parser and serializer with zero external runtime dependencies.
- **Strict Validation**: Malformed JSON triggers descriptive `JsonParseException`.

---

## 🚀 Quick Start

```java
import com.msp.jsonpatchgen.JsonPatchGenerator;
import com.msp.jsonpatchgen.JsonPatchOperation;
import java.util.List;

String sourceJson = """
    {
      "name": "Alice",
      "roles": ["USER"],
      "config": { "retries": 3 }
    }
    """;

String targetJson = """
    {
      "name": "Alice",
      "email": "alice@example.com",
      "roles": ["USER", "ADMIN"],
      "config": { "retries": 5 }
    }
    """;

// 1. Generate strongly-typed patch operations
List<JsonPatchOperation> patch = JsonPatchGenerator.generatePatch(sourceJson, targetJson);
for (JsonPatchOperation op : patch) {
    System.out.printf("%s on %s -> %s%n", op.op(), op.path(), op.value());
}

// 2. Generate RFC 6902 JSON patch string
String patchJson = JsonPatchGenerator.generatePatchJson(sourceJson, targetJson);
System.out.println(patchJson);
// Output:
// [{"op":"add","path":"/email","value":"alice@example.com"},
//  {"op":"add","path":"/roles/1","value":"ADMIN"},
//  {"op":"replace","path":"/config/retries","value":5}]
```

---

## 🧪 Testing

```bash
.\gradlew.bat :modules:skill-diff-json-patch-generator:test
```
