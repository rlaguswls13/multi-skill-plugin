# 📦 `skill-sql-formatter`

> **외부 라이브러리 없이 SQL 키워드 대문자 변환, 주요 절 줄바꿈, 서브쿼리 계층별 인덴테이션 및 문자열 리터럴/주석 원형을 완벽 보존하는 순수 Java 21 SQL 포매터**

- **아티팩트 좌표**: `com.msp:skill-sql-formatter:1.0.0`
- **개별 JAR 산출물**: `modules/skill-sql-formatter/build/libs/skill-sql-formatter-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **SQL 키워드 자동 대문자화 (Capitalization)**:
   - `SELECT`, `FROM`, `WHERE`, `GROUP BY`, `ORDER BY`, `JOIN`, `INNER JOIN`, `LEFT JOIN`, `INSERT INTO`, `UPDATE`, `SET`, `DELETE FROM`, `UNION ALL`, `CASE`, `WHEN` 등
2. **리터럴 및 주석 안전 보존 (Verbatim Preservation)**:
   - 문자열 리터럴(`'...'`, `''` 이스케이프 포함) 내부의 공백 및 텍스트는 절대 대문자로 변경하거나 수정하지 않음
   - 한 줄 주석(`-- ...`) 및 블록 주석(`/* ... */`) 완벽 보존
3. **서브쿼리 계층 인덴테이션 (Hierarchical Indentation)**:
   - 중첩 서브쿼리 `(SELECT ...)`를 자동으로 감지하여 계층별 인덴트 깊이 적용
   - 함수 인자나 `IN (1, 2, 3)` 같은 인라인 괄호는 콤팩트하게 유지
4. **유연한 포맷 설정 (`SqlFormatOptions`)**:
   - 인덴트 크기(기본 4칸), 키워드 대문자화 여부, 컬럼 목록 콤마 줄바꿈, AND/OR 줄바꿈 설정 가능

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.sqlformat.*;

String dirtySql = "select u.id, u.name from users u " +
                  "left join orders o on u.id = o.user_id " +
                  "where u.active = 1 and o.status = 'PENDING' " +
                  "order by u.id desc";

// 기본 포맷팅
String prettySql = SqlFormatter.format(dirtySql);
System.out.println(prettySql);

/*
SELECT
    u.id,
    u.name
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE
    u.active = 1
    AND o.status = 'PENDING'
ORDER BY
    u.id DESC
*/

// 커스텀 옵션 (2칸 들여쓰기)
SqlFormatOptions options = SqlFormatOptions.builder()
        .indentSpaces(2)
        .breakAfterSelectComma(false)
        .build();

String customFormatted = SqlFormatter.format(dirtySql, options);
```
