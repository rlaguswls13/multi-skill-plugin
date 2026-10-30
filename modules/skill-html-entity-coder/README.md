# 🛡️ HTML Entity Coder (`skill-html-entity-coder`)

> High-performance, zero-dependency, pure Java 21 HTML entity escape and unescape utility.

---

## 🌟 Key Features

1. **Fast Single-Pass Scanner**:
   - Zero object allocation when escaping or unescaping strings without entities.
2. **Standard Reserved Escape**:
   - Safely escapes `&`, `<`, `>`, `"`, `'` preventing Cross-Site Scripting (XSS) and XML injection.
3. **Comprehensive Entity Decoding**:
   - Named entities (`&amp;`, `&quot;`, `&copy;`, `&nbsp;`, `&euro;`, Greek letters, mathematical symbols, etc.).
   - Decimal numeric entities (`&#169;`, `&#39;`).
   - Hexadecimal numeric entities (`&#xA9;`, `&#x20AC;`, `&#x1F600;`).
   - Full support for astral plane Unicode surrogate pairs (e.g. emojis).
4. **Zero Dependencies**:
   - Pure Java 21 standard library.

---

## 🚀 Quick Start

```java
import com.msp.htmlentity.HtmlEntityCoder;

// 1. Escape HTML
String safeHtml = HtmlEntityCoder.escape("<script>alert('XSS & fun');</script>");
// Returns: &lt;script&gt;alert(&apos;XSS &amp; fun&apos;);&lt;/script&gt;

// 2. Unescape Named & Numeric Entities
String text = HtmlEntityCoder.unescape("Price: 100 &euro; &#xA9; 2026 &amp; Happy &#x1F600;");
// Returns: Price: 100 € © 2026 & Happy 😀

// 3. Round-trip
assert HtmlEntityCoder.unescape(safeHtml).equals("<script>alert('XSS & fun');</script>");
```
