# JSON Flatten & Unflatten (`skill-json-flatten-unflatten`)

복잡하게 중첩된 JSON 형식의 `Map`과 `List` 계층 구조를 점(`.`) 및 배열 인덱스(`[i]`) 표기법 기반의 1차원 평탄화(Flat) 맵으로 변환하고, 역으로 원본 계층 구조로 완벽히 복원(Unflatten)하는 순수 Java 21 단일 기술 모듈입니다.

Spring Cloud Config, 설정 파일 병합, 검색 색인 인덱싱(Elasticsearch 등), 요청 쿼리 파라미터 변환에 매우 유용합니다.

## 주요 기능

- **다차원 중첩 구조 평탄화 (Flatten)**:
  - `Map<String, ?>` 내부의 Map, List, 원시값들을 `user.address[0]` 형태의 키로 평탄화
  - 삽입 순서(Insertion-Order) 보장 (`LinkedHashMap`)
- **계층 구조 완벽 복원 (Unflatten)**:
  - 평탄화된 키 경로를 파싱하여 원본의 중첩 `Map`과 `List` 트리 구조로 복원
  - 다차원 배열(`matrix[0][1]`), 리스트 내 객체(`users[0].name`) 완벽 지원
- **사용자 정의 구분자 지원**:
  - 기본 점(`.`) 구분자 외에 슬래시(`/`) 등 임의의 구분자 지정 가능
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리만 사용

## 사용 예제

```java
import com.msp.jsonflatten.JsonFlattener;
import java.util.List;
import java.util.Map;

// 1. 중첩 구조 준비
Map<String, Object> nested = Map.of(
    "user", Map.of(
        "name", "Alice",
        "address", List.of("Seoul", "Gangnam")
    )
);

// 2. 평탄화 (Flatten)
Map<String, Object> flat = JsonFlattener.flatten(nested);
System.out.println(flat);
// 출력:
// {
//   "user.name": "Alice",
//   "user.address[0]": "Seoul",
//   "user.address[1]": "Gangnam"
// }

// 3. 역평탄화 / 복원 (Unflatten)
Map<String, Object> restored = JsonFlattener.unflatten(flat);
System.out.println(restored.equals(nested)); // true

// 4. 커스텀 구분자 사용 예제
JsonFlattener slashFlattener = JsonFlattener.withSeparator("/");
Map<String, Object> slashFlat = slashFlattener.flattenMap(nested);
// "user/address[0]": "Seoul"
```
