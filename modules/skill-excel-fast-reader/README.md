# Fast Excel (.xlsx) Zero-Dependency Reader (`skill-excel-fast-reader`)

Apache POI 등 무거운 외부 의존성(30MB+) 없이 순수 Java 21 표준 라이브러리(`ZipInputStream` + `XMLStreamReader` StAX)만으로 엑셀(.xlsx) 시트 데이터를 초고속 파싱하는 경량 모듈입니다.

## Features
- **Zero Dependencies**: 순수 Java 21 내장 라이브러리 기반 (외부 종속성 없음).
- **Streaming StAX Parser**: 메모리 사용량을 최소화하는 스트리밍 XML 파싱 구조.
- **SharedStrings & InlineStr 지원**: 일반적인 엑셀 문자열 저장 방식 모두 투명하게 지원.
- **FastExcelWriter**: 테스트 및 경량 엑셀 생성을 위한 생성 유틸리티 기본 내장.

## Usage

```java
// 1. InputStream으로부터 엑셀 행 데이터 읽기
List<ExcelRow> rows = FastExcelReader.readRows(fileInputStream);

for (ExcelRow row : rows) {
    System.out.println("Row #" + row.rowNumber() + ": " + row.getCell(0) + ", " + row.getCell(1));
}
```
