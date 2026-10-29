# 📦 `skill-semantic-version`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 SemVer 2.0.0 정식 명세 파서, 완벽한 우선순위 비교기(Comparable), 버전 증가(Bump), 그리고 범위 평가기(^, ~, >=, <=, ||, -)를 제공하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-semantic-version:1.0.0`
- **개별 JAR 산출물**: `modules/skill-semantic-version/build/libs/skill-semantic-version-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`SemVer` (SemVer 2.0.0 명세 완벽 준수 모델)**:
   - 메이저, 마이너, 패치, 사전 릴리스(`preRelease`), 빌드 메타데이터(`buildMetadata`) 완전 지원
   - `Comparable<SemVer>`: 공식 SemVer 2.0.0 명세에 따른 완벽한 우선순위 비교
     - 빌드 메타데이터는 우선순위 비교 시 완전 무시
     - 사전 릴리스 식별자 점(`.`) 단위 숫자/문자 비교 및 길이 평가 지원
   - 버전 증가 편의 메서드: `bumpMajor()`, `bumpMinor()`, `bumpPatch()`, `withPreRelease()`, `withBuildMetadata()`
   - 상태 플래그: `isStable()`, `isPreRelease()`, `isInitialDevelopment()`
2. **`VersionRange` (버전 범위 평가기)**:
   - **Caret (`^`) 범위**: `^1.2.3` (>=1.2.3 <2.0.0), `^0.2.3` (>=0.2.3 <0.3.0), `^0.0.3` (>=0.0.3 <0.0.4)
   - **Tilde (`~`) 범위**: `~1.2.3` (>=1.2.3 <1.3.0), `~1.2` (>=1.2.0 <1.3.0), `~1` (>=1.0.0 <2.0.0)
   - **비교 연산자**: `>=`, `<=`, `>`, `<`, `=`
   - **복합 조건**: 공백 기반 `AND` (예: `>=1.0.0 <2.0.0`), 파이프 기반 `OR` (예: `^1.0.0 || ^2.0.0`)
   - **하이픈 범위**: `1.0.0 - 2.0.0`
   - **와일드카드**: `*`, `1.x`, `1.2.*`

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.semver.*;

// 1. SemVer 파싱 및 비교
SemVer v1 = SemVer.parse("1.2.3-rc.1+build.100");
SemVer v2 = SemVer.parse("1.2.3");

System.out.println(v1.isLessThan(v2)); // true (사전 릴리스는 정식 릴리스보다 낮음)

// 2. 버전 판올림 (Bump)
SemVer nextMinor = v2.bumpMinor(); // "1.3.0"
SemVer nextMajor = v2.bumpMajor(); // "2.0.0"

// 3. 버전 범위(VersionRange) 만족 여부 평가
boolean ok1 = VersionRange.satisfies("^1.2.0", "1.5.3");          // true
boolean ok2 = VersionRange.satisfies("~1.2.0", "1.2.9");          // true
boolean ok3 = VersionRange.satisfies(">=2.0.0 <3.0.0", "2.1.4");  // true
boolean ok4 = VersionRange.satisfies("^1.0.0 || ^2.0.0", "2.5.0"); // true

// 4. 사전 컴파일된 Range 재사용
VersionRange range = VersionRange.parse("^1.2.3");
boolean matches = range.isSatisfiedBy(v2); // true
```
