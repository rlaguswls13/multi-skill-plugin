# 📦 `skill-table-data-copier`

> **엑셀(`.xlsx`, `.csv`) 또는 텍스트(`.txt`) 테이블 데이터를 입력받아, 각 열(Column)을 원하는 타겟 DB 컬럼명으로 매핑·치환한 뒤 화면 미리보기(API) 및 Access DB / JDBC DB로 간편하게 `INSERT (Copy)` 해주는 독립 `.jar` 모듈**

- **아티팩트 좌표**: `com.msp:skill-table-data-copier:1.0.0`
- **개별 JAR 산출물**: `modules/skill-table-data-copier/build/libs/skill-table-data-copier-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 3대 핵심 고려사항 구현 명세

### (1) Access DB 및 일반 JDBC 접속 정보 (`DbConnectionConfig`)
- **MS Access DB (`.accdb`, `.mdb`) 간편 설정**:
  ```java
  DbConnectionConfig accessConfig = DbConnectionConfig.forAccessFile(
      "C:/data/company.accdb",
      "", // username
      "", // password
      TransactionPolicy.CONTINUE_ON_ERROR // 에러 행은 건너뛰고 정상 행만 커밋 (또는 ALL_OR_NOTHING)
  );
  ```
- **일반 JDBC (MariaDB, MySQL, PostgreSQL, Oracle, MSSQL, H2 등)**:
  ```java
  DbConnectionConfig jdbcConfig = DbConnectionConfig.forJdbc(
      DbType.MARIADB,
      "jdbc:mariadb://localhost:3306/app_db",
      "db_user",
      "db_pass",
      TransactionPolicy.ALL_OR_NOTHING
  );
  ```

### (2) 치환 시 화면에 표기할 방식 — API 방식 통일 (`ApiResponse<T>`)
화면(웹 프론트엔드 또는 데스크톱 UI)에서 일관된 API 구조로 표기할 수 있도록 3단계 메서드가 모두 동일한 `ApiResponse<T>` 및 `.toJson()` 직렬화를 제공합니다:
1. **`inspectSource(input, maxSampleRows)`**: 원본 엑셀/TXT의 열 인덱스, 원본 헤더명, 샘플 행 데이터를 반환하여 화면에서 매핑 대상을 선택하도록 표기
2. **`previewSubstitution(input, targetTable, mappings, maxPreviewRows)`**:
   - `columnTransitions`: 원본 헤더명(`"사번"`) → 치환할 컬럼명(`"EMP_NO"`), 변환 전/후 샘플 값(`" 1001 "` → `"1001"`)
   - `substitutedRows`: 치환된 컬럼명을 Key로 갖는 행 데이터(`Map<String, Object>`) 및 렌더링된 `INSERT INTO ...` SQL 미리보기
   - `preparedSqlTemplate`: `INSERT INTO TB_EMPLOYEE (EMP_NO, EMP_NAME, DEPT_CD) VALUES (?, ?, ?)`

### (3) 결과 알림 및 에러 발생 시 리턴 방식 (`ResultNotification` & `CopyErrorDetail`)
- **결과 알림 (`ResultNotification`)**:
  - `level`: `SUCCESS` | `WARNING` | `ERROR`
  - `code`: `COPY_COMPLETED`, `COPY_PARTIAL_WARNING`, `DB_CONNECTION_ERROR`, `DATA_CONSTRAINT_ERROR` 등
  - `title` / `message`: UI 토스트/알림창에 즉시 바인딩 가능한 한글 제목 및 상세 메시지
- **행 단위 에러 상세 (`CopyErrorDetail`)**:
  - `rowNumber`: 에러가 발생한 행 번호 (전역 DB 접속 오류는 `0`)
  - `errorCategory`: `DB_CONNECTION_ERROR`, `SQL_SYNTAX_OR_SCHEMA_ERROR`, `DATA_CONSTRAINT_ERROR`, `MAPPING_TRANSFORM_ERROR`, `FILE_PARSE_ERROR`
  - `targetColumn` / `rejectedValue`: 에러를 유발한 타겟 컬럼명과 입력 값
  - `sqlState` / `vendorErrorCode`: JDBC 표준 SQLState 및 DB 에러 코드
  - `rowSnapshot`: 실패한 행의 전체 치환 데이터 (`Map<String, Object>`) — 실패건 재시도/엑셀 다운로드에 즉시 활용 가능
- **콜백 리스너 (`CopyResultListener`)**:
  - `onRowSuccess`, `onRowError`, `onComplete` 콜백 인터페이스로 실시간 진행률 및 에러 알림 연동 가능

---

## 2. 사용 예제 (Quick Start)

```java
import com.msp.tablecopier.api.*;
import com.msp.tablecopier.db.*;
import com.msp.tablecopier.mapping.*;
import com.msp.tablecopier.result.*;
import java.nio.file.Path;
import java.util.List;

TableDataCopierApi api = new TableDataCopierApi();

// 1. 엑셀(.xlsx) 또는 텍스트(.txt) 입력 준비
SourceDataInput input = SourceDataInput.fromFile(Path.of("employees.xlsx"));

// 2. 원하는 타겟 컬럼명으로 치환 규칙 정의
List<ColumnMappingRule> mappings = List.of(
    ColumnMappingRule.byHeader("사원번호", "EMP_NO"),
    ColumnMappingRule.byHeader("성명", "EMP_NAME"),
    ColumnMappingRule.of(2, "부서코드", "DEPT_CD", ValueTransformRule.UPPER, "COMMON")
);

// 3. [화면 표기용 API] 치환 미리보기 조회 (JSON 변환 지원)
ApiResponse<SubstitutionPreviewResponse> preview = api.previewSubstitution(
    input, "TB_EMPLOYEE", mappings, 10
);
System.out.println(preview.toJson());

// 4. [DB Copy 실행 및 결과/에러 알림 리턴]
DbConnectionConfig dbConfig = DbConnectionConfig.forAccessFile("C:/db/hr.accdb");
ApiResponse<CopyExecutionResponse> result = api.executeCopy(
    input, dbConfig, "TB_EMPLOYEE", mappings
);

if (!result.success()) {
    System.err.println("[알림] " + result.notification().title() + ": " + result.notification().message());
    for (CopyErrorDetail err : result.data().errors()) {
        System.err.printf(" - %d행 오류 (%s): %s | 데이터=%s%n",
            err.rowNumber(), err.errorCategory(), err.userMessage(), err.rowSnapshot());
    }
}
```
