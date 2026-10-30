# skill-csv-schema-validator

대용량 CSV 파일을 행 단위 스트리밍으로 읽으며 선언적 컬럼 스키마(타입: INTEGER/DECIMAL/DATE/EMAIL/STRING, 필수값 여부, 최소/최대 범위, 정규식)를 검증하고 행 단위 상세 오류 리포트를 산출하는 단일 기술 모듈입니다.

## 주요 기능
- `CsvSchema.builder().column("email", DataType.EMAIL, true).build()`: 선언적 스키마 정의
- `CsvSchemaValidator.validate(reader, schema)`: OOM 없는 스트리밍 검증
- `ValidationReport`: `isValid()`, `errorCount()`, 행 번호 및 원인 상세 에러 목록 제공
