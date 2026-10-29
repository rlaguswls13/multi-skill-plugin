# 📦 `skill-common-result`

> **외부 프로그램 간 예외 전파 없는 함수형 결과 래퍼(`Result<T, E>`), 예외 안전 실행기(`Try<T>`), 표준 에러 코드 계약(`ErrorCode`), 누적 검증기(`ValidationResult`), 그리고 표준 REST 엔벨로프(`ApiEnvelope`)를 제공하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-common-result:1.0.0`
- **개별 JAR 산출물**: `modules/skill-common-result/build/libs/skill-common-result-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`Result<T, E>`**: Java 21 Sealed Interface 기반 함수형 성공/실패 래퍼 (`map`, `flatMap`, `recover`, `fold`, `filter`)
2. **`Try<T>`**: Checked Exception까지 캡슐화하여 예외 안전 로직 실행 및 `Result` 변환 지원
3. **`ErrorCode`**: `CommonErrorCode` (400, 401, 404, 500 등) 및 `CustomErrorCode`
4. **`ValidationResult<T>`**: 여러 필드의 유효성 검증 실패(`ValidationError`)를 즉시 실패하지 않고 한 번에 모아서 반환
5. **`ApiEnvelope<T>`**: 통일된 REST JSON 응답 엔벨로프 및 외부 JSON 라이브러리 없는 내장 `.toJson()` 직렬화 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.result.*;
import com.msp.result.error.*;
import com.msp.result.validation.*;
import com.msp.result.api.*;

// 1. Result 체이닝
Result<Integer, String> result = Result.success("100")
    .map(Integer::parseInt)
    .filter(n -> n > 50, n -> "Value must be > 50");

// 2. Try 예외 안전 실행
Result<String, ErrorCode> safeResult = Try.of(() -> Files.readString(Path.of("config.json")))
    .toResult(err -> CustomErrorCode.of("FILE_READ_ERR", err.getMessage(), 500));

// 3. 다중 유효성 검증
ValidationResult<UserForm> validation = ValidationResult.of(userForm)
    .checkNotEmpty(userForm.username(), "username", "아이디는 필수입니다.")
    .check(u -> u.age() >= 19, "age", userForm.age(), "성인만 가입 가능합니다.")
    .build();

if (validation.isInvalid()) {
    System.err.println("검증 실패 목록: " + validation.errors());
}

// 4. REST JSON 응답 직렬화
ApiEnvelope<String> response = ApiEnvelope.ok("처리 완료");
System.out.println(response.toJson());
```
