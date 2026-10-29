# 📦 `skill-sql-builder-lite`

> **외부 ORM이나 무거운 종속성 없이 순수 Java 21 표준 라이브러리로 동적 쿼리 조립(`andIf`, `orIf`), IN 절 자동 확장(`status IN (?, ?, ?)`), 페이징, 그리고 JDBC `PreparedStatement` 파라미터 바인딩을 제공하는 경량 SQL 빌더 라이브러리**

- **아티팩트 좌표**: `com.msp:skill-sql-builder-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-sql-builder-lite/build/libs/skill-sql-builder-lite-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`SqlBuilders` & `SqlBuilder`**:
   - `SELECT`, `INSERT`, `UPDATE`, `DELETE`를 위한 유려한 Fluent API
2. **동적 조건 처리 (`whereIf`, `andIf`, `orIf`)**:
   - 첫 번째 활성 조건에 자동으로 `WHERE`가 붙으며, `WHERE 1=1`과 같은 불필요한 관용구가 전혀 필요 없음
   - 모든 조건이 false일 경우 `WHERE` 절 자체를 생략
3. **IN 절 자동 확장 (`whereIn`, `andIn`, `whereNotIn`)**:
   - 컬렉션 크기에 맞춰 `IN (?, ?, ?)` 플레이스홀더 동적 생성
   - 빈 컬렉션 전달 시 문법 오류 없이 `1 = 0` (또는 `1 = 1`)으로 안전 처리
4. **페이징 및 정렬 (`page(page, size)`, `limit`, `offset`, `orderBy`)**:
   - 1-based 페이지 기반의 간편한 오프셋/리밋 계산
5. **`BoundSql` & `SqlBinder`**:
   - 불변 레코드 `BoundSql(String sql, List<Object> parameters)` 산출
   - JDBC `PreparedStatement`에 `boundSql.bindTo(ps)` 단 한 줄로 1-based 순차 바인딩

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.sql.*;
import java.util.List;

// 1. 동적 SELECT 및 IN 절 자동 확장, 페이징
String keyword = "admin";
Integer minAge = 20;
List<String> statuses = List.of("ACTIVE", "PENDING");

BoundSql boundSql = SqlBuilders.select("id", "username", "email", "created_at")
    .from("users u")
    .leftJoin("user_profiles p", "p.user_id = u.id")
    .andIf(keyword != null, "username LIKE ?", "%" + keyword + "%")
    .andIf(minAge != null, "age >= ?", minAge)
    .andIn("status", statuses)
    .orderBy("created_at DESC")
    .page(1, 10)
    .build();

System.out.println("SQL: " + boundSql.sql());
// SELECT id, username, email, created_at FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id WHERE username LIKE ? AND age >= ? AND status IN (?, ?) ORDER BY created_at DESC LIMIT ? OFFSET ?

System.out.println("Params: " + boundSql.parameters());
// [%admin%, 20, ACTIVE, PENDING, 10, 0]

// 2. JDBC PreparedStatement 파라미터 자동 바인딩
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(boundSql.sql())) {
    boundSql.bindTo(ps); // 1-based 파라미터 자동 매핑
    try (ResultSet rs = ps.executeQuery()) {
        // 결과 처리
    }
}

// 3. UPDATE 및 INSERT
BoundSql updateSql = SqlBuilders.update("users")
    .set("last_login_at", System.currentTimeMillis())
    .setSql("login_count = login_count + 1")
    .where("id = ?", 100)
    .build();
```
