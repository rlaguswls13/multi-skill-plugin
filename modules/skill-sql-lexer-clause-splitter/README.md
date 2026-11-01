# 📦 `skill-sql-lexer-clause-splitter`

> **순수 Java 21 기반 SQL 어휘 분석 및 최상위 절(Clause) 분할기**

- **아티팩트 좌표**: `com.msp:skill-sql-lexer-clause-splitter:1.0.0`
- **개별 JAR 산출물**: `modules/skill-sql-lexer-clause-splitter/build/libs/skill-sql-lexer-clause-splitter-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **지능형 SQL 렉서 & 토크나이저**:
   - **괄호 중첩 추적**: 서브쿼리 `(SELECT ... FROM ... WHERE ...)` 내부의 키워드는 분할하지 않고 본문으로 유지.
   - **문자열 리터럴 경계 보존**: 따옴표(`'...'`, `"..."`, ``` `...` ```) 안의 키워드 무시 및 이스케이프 지원.
   - **SQL 주석 완벽 필터링**: 라인 주석(`-- ...`, `# ...`) 및 블록 주석(`/* ... */`) 내부 키워드 무시.
   - **원문 대소문자 보존**: 원본 SQL의 키워드 대소문자를 그대로 유지하면서 대소문자 무구분 매칭.
2. **지원 절 (Clause Types)**:
   - `SELECT`, `FROM`, `WHERE`, `GROUP BY`, `HAVING`, `ORDER BY`, `LIMIT`, `OFFSET`
   - `UNION ALL`, `UNION`
   - `INSERT INTO`, `VALUES`
   - `UPDATE`, `SET`
   - `DELETE FROM`, `WITH`, `RETURNING`
3. **불변 쿼리 변형 API (Modification Helper)**:
   - `appendConditionToWhere(condition, operator)`: 기존 WHERE 절에 조건 추가(`AND`/`OR`) 또는 적절한 위치에 새 WHERE 절 자동 생성.
   - `replaceClause(type, newBody)`: 특정 절 본문 치환.
   - `removeClause(type)`: 특정 절 삭제.
   - `toSql()` / `toInlineSql()`: 수정된 절을 포맷팅하여 SQL 문자열 재생성.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.clausesplit.ClauseType;
import com.msp.clausesplit.SqlClauseSplitter;
import com.msp.clausesplit.SqlSplitResult;

public class SqlClauseSplitterExample {
    public static void main(String[] args) {
        String query = """
                SELECT u.id, u.name, (SELECT count(*) FROM orders o WHERE o.user_id = u.id) AS orders
                FROM users u
                WHERE u.status = 'ACTIVE'
                ORDER BY u.id DESC
                LIMIT 50
                """;

        // 1. 최상위 절 분할
        SqlSplitResult result = SqlClauseSplitter.split(query);

        System.out.println("Has WHERE: " + result.hasClause(ClauseType.WHERE));
        System.out.println("FROM clause: " + result.getClauseBody(ClauseType.FROM).orElse(""));

        // 2. WHERE 조건 동적 추가
        SqlSplitResult modified = result.appendConditionToWhere("u.dept_id = 10", "AND");
        System.out.println("\nModified SQL:\n" + modified.toSql());

        // 3. 절 교체
        SqlSplitResult paginated = modified.replaceClause(ClauseType.LIMIT, "100");
        System.out.println("\nPaginated SQL:\n" + paginated.toInlineSql());
    }
}
```
