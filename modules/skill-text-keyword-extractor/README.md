# 🔍 skill-text-keyword-extractor

> **Pure Java 21 TF-based keyword extractor with Korean and English stopword filtering**  
> Lightweight keyword extractor that normalizes punctuation, cleans Korean postpositions (조사), removes multilingual stopwords, and ranks terms by Term Frequency (TF).

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 regular expressions and collections only.
- **Multilingual Stopword Preloads**: Built-in comprehensive sets of Korean particles/stopwords and English stopwords.
- **Smart Korean Postposition Stripping**: Normalizes words such as `"인공지능은"`, `"인공지능을"`, `"인공지능의"` to the common stem `"인공지능"`.
- **Term Frequency (TF) Scoring**: Calculates relative term frequency and sorts keywords descending by rank.
- **Extensible**: Seamlessly add custom domain stopwords using `.withStopwords(...)`.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-text-keyword-extractor:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Extracting Top Keywords

```java
import com.msp.keyword.KeywordExtractor;
import com.msp.keyword.KeywordScore;
import java.util.List;

KeywordExtractor extractor = new KeywordExtractor();

String article = """
    Java 21 delivers virtual threads, pattern matching, and record patterns.
    Developers utilizing Java 21 can build highly scalable backend services.
    Java continues to evolve rapidly.
""";

List<KeywordScore> topKeywords = extractor.extractTopKeywords(article, 3);
for (KeywordScore kw : topKeywords) {
    System.out.printf("Keyword: %-15s | Count: %2d | Score: %.4f%n",
            kw.word(), kw.count(), kw.score());
}
```

### 2. Custom Domain Stopwords

```java
KeywordExtractor domainExtractor = extractor.withStopwords(Set.of("company", "platform", "service"));

List<KeywordScore> results = domainExtractor.extractTopKeywords(documentText, 5);
```
