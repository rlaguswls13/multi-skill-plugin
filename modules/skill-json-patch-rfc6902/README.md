# RFC 6902 JSON Patch & RFC 6901 JSON Pointer (`skill-json-patch-rfc6902`)

순수 Java 21 표준 라이브러리(Jackson/Gson 런타임 의존성 Zero) 기반의 RFC 6902 JSON Patch 및 RFC 6901 JSON Pointer 구현체입니다.

Java 표준 컬렉션(`Map<String, Object>` 및 `List<Object>`) 기반 문서에 대해 `add`, `remove`, `replace`, `copy`, `move`, `test` 작업을 지원하며, RESTful HTTP `PATCH` 요청을 안전하고 직관적으로 처리합니다.

## Features

- **RFC 6902 표준 6개 연산 완벽 지원**:
  - `add`: Map 속성 추가/대체, List 특정 인덱스 삽입, List 끝 추가(`"-"`), 루트 문서 교체(`""`)
  - `remove`: Map 속성 제거, List 특정 인덱스 항목 제거
  - `replace`: 기존 값 변경 (키/인덱스 부재 시 예외 발생)
  - `move`: 소스 위치에서 제거 후 타깃 위치로 이동 (하위 경로 이동 금지 검증)
  - `copy`: 소스 위치 객체 깊은 복사(Deep Clone) 후 타깃 위치에 추가
  - `test`: 값 일치 여부 검증 (불일치 시 `JsonPatchTestFailedException` 발생)
- **RFC 6901 JSON Pointer 완벽 지원**:
  - 특수문자 이스케이프 지원 (`~1` -> `/`, `~0` -> `~`)
  - 루트 경로(`""`), 계층 트리 탐색 및 유효성 검사
- **Zero Runtime Dependencies**: 외부 JSON 파서 없이 표준 Java 컬렉션만으로 구동

## Usage

```java
import com.msp.jsonpatch.JsonPatch;
import com.msp.jsonpatch.JsonPatchOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 1. 원본 문서 준비 (표준 Map / List)
Map<String, Object> document = new LinkedHashMap<>();
document.put("title", "Initial Title");
document.put("tags", new ArrayList<>(List.of("java", "backend")));

// 2. 패치 연산 정의
List<JsonPatchOperation> patchOps = List.of(
    JsonPatchOperation.test("/title", "Initial Title"),
    JsonPatchOperation.replace("/title", "Advanced JSON Patch"),
    JsonPatchOperation.add("/tags/-", "rfc6902"),
    JsonPatchOperation.add("/author", "Alice")
);

// 3. 패치 적용
Object patched = JsonPatch.apply(document, patchOps);

System.out.println(patched);
// {title=Advanced JSON Patch, tags=[java, backend, rfc6902], author=Alice}
```
