# 📦 `skill-html-tag-stripper`

> **외부 라이브러리 없이 스크립트/스타일 본문 완전 제거, 블록 태그 줄바꿈 분리, HTML 엔티티(명명·10진수·16진수) 언이스케이프, 공백 정규화를 완벽 지원하는 검색 색인(Full-Text Indexing)용 순수 Java 21 HTML 스트리퍼**

- **아티팩트 좌표**: `com.msp:skill-html-tag-stripper:1.0.0`
- **개별 JAR 산출물**: `modules/skill-html-tag-stripper/build/libs/skill-html-tag-stripper-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **위험 태그 본문 완전 제거**:
   - `<script>...</script>` 및 `<style>...</style>` 내부 스크립트/CSS 코드 완전 소거
   - `<!-- ... -->` 주석 소거
2. **블록 태그 줄바꿈 변환 (Word Separation)**:
   - `<p>`, `<div>`, `<br>`, `<h1>`~`</h6>`, `<li>`, `<tr>` 등을 적절한 줄바꿈(`\n`) 또는 공백으로 변환하여 인접 단어가 서로 붙는 현상(`HelloWorld`) 방지
3. **HTML 엔티티 자동 디코딩 (Unescaping)**:
   - 명명 엔티티: `&nbsp;`, `&amp;`, `&lt;`, `&gt;`, `&quot;`, `&euro;`, `&copy;` 등
   - 숫자 엔티티: 10진수 `&#39;`, 16진수 `&#x20AC;`, `&#x2F;` 등 유효 코드포인트 자동 변환
4. **검색 엔진 친화적 공백 정규화**:
   - 불필요한 연속 공백 축소, 중복 빈 줄 정리
   - 한 줄 추출 전용 `stripToSingleLine()` 메서드 지원
5. **비정형 HTML 내결함성**:
   - 닫히지 않은 태그, 속성 따옴표 내 `>` 포함 태그, 수학 수식(`3 < 5`) 보존

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.htmlstrip.*;

String dirtyHtml = """
    <article>
        <h1>Java &amp; Spring 가이드</h1>
        <style>body { font-size: 14px; }</style>
        <p>환영합니다! 가격은 &euro;50이며 &copy; 2026 MSP입니다.</p>
        <script>alert('xss');</script>
    </article>
    """;

// 1. 기본 줄바꿈 보존 스트리핑
String cleanText = HtmlTagStripper.strip(dirtyHtml);
System.out.println(cleanText);
/*
Java & Spring 가이드
환영합니다! 가격은 €50이며 © 2026 MSP입니다.
*/

// 2. 단일 라인 요약 추출 (검색 엔진 제목/요약문용)
String singleLine = HtmlTagStripper.stripToSingleLine(dirtyHtml);
System.out.println(singleLine);
// "Java & Spring 가이드 환영합니다! 가격은 €50이며 © 2026 MSP입니다."
```
