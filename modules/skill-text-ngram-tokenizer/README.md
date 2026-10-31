# 🔤 skill-text-ngram-tokenizer

문자 및 단어 단위의 N-gram 분해와 Jaccard / Cosine 유사도 계측을 제공하는 고성능 NLP 경량 유틸리티 라이브러리입니다.

## ✨ 주요 기능
- **문자/단어 단위 N-gram 토큰화**: Unigram, Bigram, Trigram 등 임의 길이($N$)의 슬라이딩 윈도우 토큰 분해
- **Jaccard 유사도 계산**: 집합 교집합/합집합 비율 기반($|A \cap B| / |A \cup B|$)의 오타 탐지 및 문자열 매칭
- **Cosine 유사도 계산**: N-gram 빈도 벡터 기반 코사인 유사도($\cos \theta$)로 문서/문장 유사도 정밀 비교
- **경계값 및 공백 안전성**: 불규칙 공백 자동 정규화 및 null/empty에 대한 안전한 방어 처리
- **Pure Java 21**: 외부 런타임 3rd-party 의존성 Zero

---

## 🚀 빠른 시작 (Quick Start)

### 1. 문자 및 단어 N-gram 분해
```java
import com.msp.ngram.NgramTokenizer;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        // 문자 Bigram
        List<String> charNgrams = NgramTokenizer.characterNgrams("hello", 2);
        System.out.println(charNgrams); // ["he", "el", "ll", "lo"]

        // 단어 Bigram
        List<String> wordNgrams = NgramTokenizer.wordNgrams("the quick brown fox", 2);
        System.out.println(wordNgrams); // ["the quick", "quick brown", "brown fox"]
    }
}
```

### 2. Jaccard & Cosine 유사도 계측
```java
// 2-gram 기준 문자열 자카드 유사도
double jaccard = NgramTokenizer.jaccardSimilarity("night", "nacht", 2);
System.out.printf("Jaccard: %.2f%%\n", jaccard * 100);

// 단어 1-gram 기준 코사인 유사도
double cosine = NgramTokenizer.wordCosineSimilarity(
    "machine learning models",
    "deep learning models",
    1
);
System.out.printf("Cosine: %.2f%%\n", cosine * 100);
```
