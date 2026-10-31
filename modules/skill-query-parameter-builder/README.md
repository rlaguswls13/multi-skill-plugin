# Query Parameter Builder (`skill-query-parameter-builder`)

다중값(Multi-value) 리스트 및 객체를 완벽히 지원하는 양방향 URL 쿼리스트링 직렬화/역직렬화 빌더 모듈입니다.
RFC 3986 표준 UTF-8 퍼센트 인코딩(`%20`)을 준수하여 HTTP 클라이언트 요청 조립 및 API 엔드포인트 파라미터 파싱에 최적화되어 있습니다.

## 특징
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리(`URLEncoder`, `URLDecoder`, `StandardCharsets`)만 사용
- **RFC 3986 UTF-8 표준 준수**: 공백(`%20`), 괄호, 물결표(`~`), 느낌표 등 RFC 3986 권장 규격 엄격 인코딩
- **다중값(Multi-value) 리스트 완벽 지원**: 단일 키에 복수 값 바인딩 (`?role=admin&role=manager`)
- **양방향 파싱 & 수정**:
  - `QueryParameterBuilder.parse()`: 전체 URL 또는 쿼리스트링에서 불변 `QueryParameterMap` 추출
  - `QueryParameterBuilder.from()`: 기존 쿼리스트링을 기반으로 파라미터 추가/수정/삭제 후 재빌드
- **Fragment 자동 제거**: URL 내 해시태그(`#...`) 자동 무시 및 정제

## 사용법

```java
import com.msp.paramquery.QueryParameterBuilder;
import com.msp.paramquery.QueryParameterMap;
import java.util.List;

// 1. 쿼리스트링 빌드
String query = new QueryParameterBuilder()
        .add("keyword", "스프링 & 자바")
        .add("page", 1)
        .add("tags", List.of("backend", "open-source"))
        .build();

System.out.println(query);
// 출력: ?keyword=%EC%8A%A4%ED%94%84%EB%A7%81%20%26%20%EC%9E%90%EB%B0%94&page=1&tags=backend&tags=open-source

// 2. 쿼리스트링 역직렬화 (URL 전체 지원)
QueryParameterMap map = QueryParameterBuilder.parse("https://api.example.com/v1/search" + query);
System.out.println("Keyword: " + map.get("keyword")); // "스프링 & 자바"
System.out.println("Tags: " + map.getAll("tags"));     // ["backend", "open-source"]

// 3. 기존 쿼리 파라미터 수정 및 확장
String nextUrl = QueryParameterBuilder.from(query)
        .set("page", 2)       // 2페이지로 변경
        .remove("keyword")    // keyword 제거
        .build();
System.out.println(nextUrl);
// 출력: ?page=2&tags=backend&tags=open-source
```
