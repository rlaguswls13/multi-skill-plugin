# 📦 `skill-string-phonetic-double-metaphone`

> **순수 Java 21 기반 로런스 필립스(Lawrence Philips) Double Metaphone 음성학적 인코딩 알고리즘**

- **아티팩트 좌표**: `com.msp:skill-string-phonetic-double-metaphone:1.0.0`
- **개별 JAR 산출물**: `modules/skill-string-phonetic-double-metaphone/build/libs/skill-string-phonetic-double-metaphone-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **이중 음소 키(Primary & Secondary Key) 동시 산출**:
   - 다국어(영어, 독일어, 프랑스어, 이탈리아어, 스페인어, 슬라브어계 등) 발음 변형을 감안하여 1순위(Primary) 및 2순위(Secondary) 음성학적 식별자 반환.
2. **복합 음운 규칙 완벽 처리**:
   - 첫머리 묵음 (`KN`, `GN`, `PN`, `WR`, `PS` 등)
   - 모음 앞/뒤 환경에 따른 자음 발음 변화 (`C`, `CH`, `CZ`, `G`, `GH`, `GN`, `J`, `L`, `PH`, `SC`, `SH`, `TH`, `X`, `Z` 등)
3. **유연한 음성학적 유사도 비교**:
   - `isSimilar(str1, str2)`: 1/2순위 음소 키 교차 검사를 통한 오타/발음 유사 단어 판별 (`"Knight"` == `"Night"`, `"Stephen"` == `"Steven"`).
   - `isSimilar(str1, str2, alternate)`: 1순위 전용 비교 또는 2순위 포함 여부 지정.
4. **표준 검증 데이터 완벽 일치**:
   - `Smith` -> `SM0` / `SMT`
   - `Schmidt` -> `XMT` / `SMT`
   - `Knight` -> `NT`
   - `Wrestle` -> `RSL`
   - `Alexander` -> `ALKS`

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.doublemetaphone.DoubleMetaphone;
import com.msp.doublemetaphone.DoubleMetaphoneResult;

public class DoubleMetaphoneExample {
    public static void main(String[] args) {
        // 1. 음소 인코딩
        DoubleMetaphoneResult result = DoubleMetaphone.encode("Smith");
        System.out.println("Primary: " + result.getPrimary());     // SM0
        System.out.println("Secondary: " + result.getSecondary()); // SMT

        // 2. 동음이의어 및 표기 변형 비교
        boolean isSimilar = DoubleMetaphone.isSimilar("Smith", "Schmidt");
        System.out.println("Smith ~ Schmidt: " + isSimilar); // true (공통 SMT 키 일치)

        // 3. 발음이 같은 단어 비교
        System.out.println("Knight ~ Night: " + DoubleMetaphone.isSimilar("Knight", "Night")); // true (NT)
        System.out.println("Catherine ~ Katherine: " + DoubleMetaphone.isSimilar("Catherine", "Katherine")); // true
    }
}
```
