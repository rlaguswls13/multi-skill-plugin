# 🏷️ Skill: Slug Generator (`com.msp.slug`)

Multilingual, SEO-friendly URL slug generator with Latin accent transliteration, Unicode support, ligature substitution, and word-boundary safe truncation. Pure Java 21 standard library with zero runtime dependencies.

---

## 🌟 Key Features

- **Accent Transliteration**: Decomposes Latin diacritical marks (`é` -> `e`, `ü` -> `u`, `ç` -> `c`).
- **Ligature & Symbol Expansion**: Automatically expands common symbols (`&` -> `and`, `ß` -> `ss`, `æ` -> `ae`, `@` -> `at`).
- **Multilingual Unicode Support**: Option to preserve non-Latin scripts (Hangul, Japanese, Cyrillic, etc.) or strictly filter to ASCII.
- **Word-Boundary Truncation**: Smart length capping that avoids cutting words in half.
- **Separator Normalization**: Consecutive delimiters and edge punctuation are cleanly collapsed.

---

## 🚀 Quick Start

### 1. Default ASCII Slug Generation

```java
import com.msp.slug.SlugGenerator;

String slug = SlugGenerator.generate("Java 21 & Spring Boot: The Ultimate Guide?!");
// Output: "java-21-and-spring-boot-the-ultimate-guide"

String cafe = SlugGenerator.generate("Café au Lait & Crème Brûlée Über München");
// Output: "cafe-au-lait-and-creme-brulee-uber-munchen"
```

### 2. Multilingual Unicode Mode (Korean, Japanese, etc.)

```java
import com.msp.slug.SlugGenerator;
import com.msp.slug.SlugOptions;

String koreanTitle = "자바 21 신기능 & 마이크로서비스 가이드!";
String slug = SlugGenerator.generate(koreanTitle, SlugOptions.UNICODE);
// Output: "자바-21-신기능-and-마이크로서비스-가이드"
```

### 3. Custom Delimiter & Word-Boundary Truncation

```java
import com.msp.slug.SlugGenerator;
import com.msp.slug.SlugOptions;

SlugOptions options = SlugOptions.builder()
        .separator("_")
        .maxLength(25)
        .wordBoundaryTruncation(true)
        .build();

String slug = SlugGenerator.generate("Building Scalable Cloud Native Architecture", options);
// Output: "building_scalable_cloud" (snaps back cleanly at word boundary)
```
