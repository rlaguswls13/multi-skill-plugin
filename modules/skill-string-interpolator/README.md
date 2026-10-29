# 🧵 Skill: String Interpolator (`com.msp.interpolate`)

Lightweight, high-performance string template interpolator supporting `${var}` named placeholders, nested dot notation (`${user.address.city}`), positional indexed substitutions (`{0}`), backslash escaping, and configurable missing variable fallbacks. Pure Java 21 standard library with zero runtime dependencies.

---

## 🌟 Key Features

- **Named Variable Interpolation**: Seamless `${var}` replacement with context Maps.
- **Nested Dot Notation**: Deep property traversal across nested `Map` hierarchies, Java 21 records, and JavaBean getter methods.
- **Positional Indexed Formatting**: Standard `{0}`, `{1}` parameter replacement without `MessageFormat` syntax quirks.
- **Backslash Escaping**: Safe syntax escaping (`\${var}` &rarr; `${var}`, `\{0\}` &rarr; `{0}`, `\\${var}` &rarr; `\value`).
- **Configurable Fallback Strategies**: `PRESERVE` (keep placeholder), `EMPTY` (replace with `""`), or `THROW` (`MissingVariableException`).

---

## 🚀 Quick Start

### 1. Named Placeholders & Nested Records

```java
import com.msp.interpolate.StringInterpolator;
import java.util.Map;

record Address(String city, String zipCode) {}
record User(String name, Address address) {}

User user = new User("Alice", new Address("Seoul", "04524"));
Map<String, Object> context = Map.of("user", user);

String template = "Welcome ${user.name}! Your package will ship to ${user.address.city} (${user.address.zipCode}).";
String result = StringInterpolator.interpolate(template, context);
// Output: "Welcome Alice! Your package will ship to Seoul (04524)."
```

### 2. Backslash Escaping

```java
import com.msp.interpolate.StringInterpolator;
import java.util.Map;

Map<String, Object> ctx = Map.of("name", "Bob");

String result = StringInterpolator.interpolate("Use \\${name} syntax to print: ${name}", ctx);
// Output: "Use ${name} syntax to print: Bob"
```

### 3. Positional Indexed Interpolation

```java
import com.msp.interpolate.StringInterpolator;

String message = StringInterpolator.interpolateIndexed("Hello {0}, you scored {1}%!", "Bob", 98);
// Output: "Hello Bob, you scored 98%!"
```
