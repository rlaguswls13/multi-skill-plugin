# Fast JSON Path Extractor (`skill-json-path-extractor-fast`)

Zero-dependency, high-performance lightweight JSON path extractor implemented in pure Java 21. Supports nested object keys, array indices (`users[0].address.city`), and raw JSON subtree slice extraction without heavy runtime dependencies like Jackson or Gson.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library only.
- **Deep Path Navigation**: Supports standard dot and bracket notation (`store.books[0].title`, `$.items[1].name`).
- **Raw JSON Slicing**: Instantly slices and extracts sub-objects or sub-arrays as valid JSON strings.
- **Typed Extractions**: Safe typed retrieval via `Optional<String>`, `Optional<Long>`, `Optional<Double>`, and `Optional<Boolean>`.
- **Fault-Tolerant**: Returns `Optional.empty()` on invalid paths, null values, or malformed JSON without throwing unhandled runtime exceptions.

---

## 📦 Usage Examples

### 1. Extracting Typed Values

```java
import com.msp.jsonpathfast.FastJsonPathExtractor;
import java.util.Optional;

String json = """
    {
      "order": {
        "id": 982134,
        "active": true,
        "total": 149.95,
        "items": [
          {"name": "Mechanical Keyboard", "price": 120.0},
          {"name": "Mousepad", "price": 29.95}
        ]
      }
    }
    """;

// Extract string
Optional<String> itemName = FastJsonPathExtractor.extractString(json, "order.items[0].name");
System.out.println(itemName.orElse("N/A")); // "Mechanical Keyboard"

// Extract Long
Optional<Long> orderId = FastJsonPathExtractor.extractLong(json, "order.id");
System.out.println(orderId.orElse(-1L)); // 982134

// Extract Double
Optional<Double> total = FastJsonPathExtractor.extractDouble(json, "order.total");
System.out.println(total.orElse(0.0)); // 149.95

// Extract Boolean
Optional<Boolean> active = FastJsonPathExtractor.extractBoolean(json, "order.active");
System.out.println(active.orElse(false)); // true
```

### 2. Slicing Raw Sub-JSON

```java
// Slices nested array or object as a raw JSON string
Optional<String> itemsJson = FastJsonPathExtractor.extractRawJson(json, "order.items");
// Returns: '[{"name": "Mechanical Keyboard", "price": 120.0}, {"name": "Mousepad", "price": 29.95}]'
```
