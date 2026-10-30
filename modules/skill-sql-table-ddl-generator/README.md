# SQL Table DDL Generator (`skill-sql-table-ddl-generator`)

자바 클래스 또는 레코드(Java Record)를 리플렉션으로 분석하여 PostgreSQL, MySQL, H2 방언에 맞춘 `CREATE TABLE` DDL SQL 문을 즉시 생성해 주는 초경량 DDL 생성기입니다.

## Features
- **Java Record & Standard Class 지원**: Record 컴포넌트 순서 보존 및 일반 POJO 클래스 상속 필드 분석.
- **스마트 타입 매핑**:
  - `String` -> `VARCHAR(255)` (필드명이 `content`, `text`, `description`으로 끝나면 `TEXT`)
  - `int` / `Integer` -> `INTEGER`
  - `long` / `Long` -> `BIGINT`
  - `boolean` / `Boolean` -> `BOOLEAN` (MySQL의 경우 `TINYINT(1)`)
  - `double` / `Double` / `float` -> `DOUBLE PRECISION`
  - `BigDecimal` -> `DECIMAL(19, 4)`
  - `Instant` / `LocalDateTime` -> `TIMESTAMP`
  - `LocalDate` -> `DATE`
  - `UUID` -> `UUID` (MySQL은 `VARCHAR(36)`)
- **자동 식별자 분석 (Primary Key)**: `id` 또는 `*Id`(예: `userId`, `orderId`) 필드를 찾아 `PRIMARY KEY` 제약조건 자동 부여.
- **자동 Snake Case 변환**: camelCase 필드명과 PascalCase 클래스명을 표준 데이터베이스 snake_case 네이밍으로 자동 변환.
- **다중 방언 지원**: `SqlDialect.POSTGRESQL`, `SqlDialect.MYSQL`, `SqlDialect.H2`.
- **Zero Runtime Dependencies**: 순수 Java 21 리플렉션 API(`java.lang.reflect.*`).

## Usage

```java
import com.msp.ddl.TableDdlGenerator;
import com.msp.ddl.SqlDialect;
import java.math.BigDecimal;
import java.time.Instant;

public record UserProfile(
    Long id,
    String userName,
    String selfDescription,
    boolean isActive,
    BigDecimal balance,
    Instant createdAt
) {}

// DDL 생성
String postgresSql = TableDdlGenerator.generateDdl(UserProfile.class, SqlDialect.POSTGRESQL);
System.out.println(postgresSql);

/*
출력 결과:
CREATE TABLE user_profile (
    id BIGINT PRIMARY KEY,
    user_name VARCHAR(255),
    self_description TEXT,
    is_active BOOLEAN,
    balance DECIMAL(19, 4),
    created_at TIMESTAMP
);
*/
```
