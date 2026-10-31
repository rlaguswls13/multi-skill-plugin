# SQL Lexer & Tokenizer (`skill-sql-lexer-tokenizer`)

순수 Java 21 표준 라이브러리(ANTLR, Calcite, JSqlParser 의존성 Zero)로 구현된 초경량 고성능 SQL 어휘 분석기(Lexer & Tokenizer)입니다.

SQL 쿼리 문자열을 키워드(KEYWORD), 식별자(IDENTIFIER), 문자열 리터럴(STRING_LITERAL), 숫자 리터럴(NUMERIC_LITERAL), 연산자(OPERATOR), 구두점(PUNCTUATION), 주석(COMMENT) 토큰 스트림으로 분해하여 쿼리 정적 분석, 포매터, 린터, 보안 필터(SQL Injection 방어 보조) 등에 활용할 수 있습니다.

## Features

- **ANSI/ISO SQL 표준 및 주요 RDBMS 방언 지원**:
  - 표준 SQL 예약어 및 주요 키워드 대소문자 무관 인식
  - 식별자: 일반 식별자 및 따옴표 식별자(MySQL 백틱 `` `col` ``, SQL Server 대괄호 `[col]`)
  - 문자열 리터럴: 작은따옴표 `'...'` (SQL 표준 `''` 이스케이프 포함) 및 큰따옴표 `"..."`
  - 숫자 리터럴: 정수, 고정소수점, 지수 표기법(`1.5e-3`), 16진수(`0x1A`)
  - 연산자: 단일 문자 및 복합 연산자(`!=`, `<>`, `<=`, `>=`, `:=`, `||`, `->>`)
  - 주석: 단일행(`--`, `#`) 및 다중행 블록(`/* ... */`)
- **정확한 위치 추적**: 각 토큰의 원본 문자열 내 시작 인덱스(0-based position) 보존
- **주석 필터링 옵션**: `tokenizeWithoutComments` 메서드로 주석이 제거된 순수 구문 토큰만 추출 가능
- **Zero Runtime Dependencies**: JVM 표준 라이브러리만으로 동작

## Usage

```java
import com.msp.sqllexer.SqlLexer;
import com.msp.sqllexer.SqlToken;
import com.msp.sqllexer.SqlTokenType;

import java.util.List;

String sql = "SELECT id, name FROM users WHERE age >= 21 AND status = 'ACTIVE';";

// 1. 전체 토큰화
List<SqlToken> tokens = SqlLexer.tokenize(sql);

for (SqlToken token : tokens) {
    System.out.printf("[%s] '%s' at offset %d%n",
        token.type(), token.value(), token.position());
}

// 2. 주석 제외 토큰화
String sqlWithComments = "-- fetch active users\nSELECT * FROM users /* fast query */";
List<SqlToken> cleanTokens = SqlLexer.tokenizeWithoutComments(sqlWithComments);
```
