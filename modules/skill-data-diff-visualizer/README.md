# Data Diff Visualizer & Markdown Reporter (`skill-data-diff-visualizer`)

두 Map 또는 계층형 JSON/객체 상태 간의 변경 내역(추가, 삭제, 수정, 불변)을 정밀 비교하고 요약 메트릭 및 Markdown 표 형식으로 시각화하는 모듈입니다.

## Features
- **Nested Path Support**: 중첩된 맵 구조를 `user.address.street` 형태의 계층형 키 경로로 자동 평탄화 비교.
- **Diff Classification**: `ADDED`(🟢), `REMOVED`(🔴), `MODIFIED`(🟡), `UNCHANGED`(⚪) 4가지 상태 판정.
- **Markdown Table Formatter**: 감사 로그, 슬랙 알림, PR 코멘트에 바로 삽입 가능한 Markdown 표 출력.
- **Zero Dependencies**: 순수 Java 21 표준 라이브러리.

## Usage

```java
DiffReport report = DataDiffVisualizer.diff(beforeMap, afterMap);

// 요약 출력: +1 -1 ~2 (unchanged: 5)
System.out.println(report.toSummaryString());

// 마크다운 표 렌더링
String markdown = report.toMarkdownTable();
System.out.println(markdown);
```
