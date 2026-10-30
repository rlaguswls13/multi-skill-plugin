# Markdown TOC Extractor (`skill-markdown-toc-extractor`)

Markdown 문서에서 제목 헤딩(`h1`~`h6`)을 추출하여 GitHub 호환 앵커 슬러그 및 계층형 목차(TOC, Table of Contents) 목록을 자동 생성하는 유틸리티입니다.

## Features
- **Code Fence Filter**: 코드 블록(```) 내의 주석이나 `#` 기호는 목차 추출 대상에서 완벽 제외.
- **Slug Deduplication**: 동일한 제목이 반복될 경우 `-1`, `-2` 슬러그 자동 부여로 앵커 링크 충돌 방지.
- **Hierarchical Markdown Generation**: 상하 계층 레벨(Level)에 따른 2-space 들여쓰기 목차 목록 자동 출력.
- **Zero Dependencies**: 순수 Java 21 표준 라이브러리(`java.util.regex`).

## Usage

```java
String markdown = "# Guide\n## Installation\n### Step 1";
String tocMarkdown = TocExtractor.generateTocMarkdown(markdown);

System.out.println(tocMarkdown);
// - [Guide](#guide)
//   - [Installation](#installation)
//     - [Step 1](#step-1)
```
