# 📦 `skill-sql-dynamic-in-clause-partitioner`

> **SQL IN-절 컬렉션 분할 및 DB 파라미터 한계(Oracle 1000개, SQLite 999개 등) 우회 쿼리 동적 생성기**

- **아티팩트 좌표**: `com.msp:skill-sql-dynamic-in-clause-partitioner:1.0.0`
- **개별 JAR 산출물**: `modules/skill-sql-dynamic-in-clause-partitioner/build/libs/skill-sql-dynamic-in-clause-partitioner-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **대규모 컬렉션 분할 (Chunking)**:
   - 5,000+ 개의 ID 목록을 설정된 청크 크기 $K$(기본 1000 또는 500) 단위로 자동 분할
   - 단일 청크: `col IN (?, ?, ?)`
   - 다중 청크: `(col IN (?, ...) OR col IN (?, ...))`
2. **이름 기반 파라미터 (:param) 지원**:
   - Spring NamedParameterJdbcTemplate 또는 MyBatis 연동 지원
   - `(col IN (:id_0_0, :id_0_1) OR col IN (:id_1_0))` 및 평탄화된 `Map<String, Object>` 파라미터 맵 자동 생성
3. **복합 키 (Composite Key) 튜플 IN 쿼리 지원**:
   - `((tenant_id, user_id) IN ((?, ?), ...) OR ...)` 형태의 다중 열 튜플 쿼리 생성
   - 튜플 IN 미지원 DBMS를 위한 `((col1 = ? AND col2 = ?) OR ...)` 대체 쿼리 분할 지원
4. **빈 컬렉션 안전 처리**:
   - `null` 또는 빈 컬렉션 입력 시 SQL Injection이나 문법 오류 없이 `1 = 0` (항상 False) 안전 절 생성

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.inpartition.CompositeKey;
import com.msp.inpartition.NamedSqlInClause;
import com.msp.inpartition.SqlInClause;
import com.msp.inpartition.SqlInClausePartitioner;

import java.util.List;

public class InPartitionerExample {
    public static void main(String[] args) {
        List<Integer> userIds = List.of(1, 2, 3, 4, 5);

        // 1. 위치 기반 파라미터 (?, 청크 크기 2로 분할)
        SqlInClause clause = SqlInClausePartitioner.partition("user_id", userIds, 2);
        System.out.println("SQL: " + clause.sql());
        // (user_id IN (?, ?) OR user_id IN (?, ?) OR user_id IN (?))
        System.out.println("Params: " + clause.parameters());
        // [1, 2, 3, 4, 5]

        // 2. 이름 기반 파라미터 (:name)
        NamedSqlInClause namedClause = SqlInClausePartitioner.partitionNamed("code", "code", List.of("A", "B", "C"), 2);
        System.out.println("Named SQL: " + namedClause.sql());
        // (code IN (:code_0_0, :code_0_1) OR code IN (:code_1_0))
        System.out.println("Named Map: " + namedClause.parameters());
        // {code_0_0=A, code_0_1=B, code_1_0=C}

        // 3. 복합 키 튜플 IN 쿼리
        List<CompositeKey> compositeKeys = List.of(
            CompositeKey.of("T1", 101),
            CompositeKey.of("T2", 102),
            CompositeKey.of("T3", 103)
        );
        SqlInClause compClause = SqlInClausePartitioner.partitionComposite(
            List.of("tenant_id", "emp_id"),
            compositeKeys,
            2
        );
        System.out.println("Composite SQL: " + compClause.sql());
        // ((tenant_id, emp_id) IN ((?, ?), (?, ?)) OR (tenant_id, emp_id) IN ((?, ?)))
    }
}
```
