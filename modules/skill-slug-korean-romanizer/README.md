# Korean Romanizer & URL Slug Generator (`skill-slug-korean-romanizer`)

국립국어원 표준 로마자 표기법에 맞춰 한글 텍스트를 알파벳으로 음차 변환하고, 블로그/게시판용 웹 친화적 URL 슬러그(Slug)를 자동 생성하는 경량 모듈입니다.

## Features
- **Unicode Decomposition**: 유니코드 음절 분해 알고리즘(초성 19자, 중성 21자, 종성 28자) 기반 표준 로마자 변환.
- **Clean URL Slug**: 특수문자 제거, 공백 하이픈 치환, 소문자 정규화 처리가 적용된 SEO 최적화 슬러그 생성.
- **Zero Dependencies**: 외부 번역 라이브러리 없이 순수 Java 21 표준 라이브러리만으로 동작.

## Usage

```java
// 1. 단순 로마자 변환
String roman = KoreanRomanizer.romanize("안녕하세요"); // "annyeonghaseyo"

// 2. 블로그 게시물 URL 슬러그 생성
String slug = KoreanRomanizer.toSlug("스프링 부트 3.0 실전 가이드!");
// "seupeuring-buteu-3-0-siljeon-gaideu"
```
