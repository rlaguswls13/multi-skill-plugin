# 🇰🇷 skill-korean-josa-picker

Korean postposition (조사) picker based on final consonant (종성 / 받침) detection with zero runtime dependencies.

## ✨ Features

- **Zero Runtime Dependencies**: 100% Pure Java 21 standard library.
- **Accurate Phonological Analysis**:
  - Hangul unicode decomposition (`(c - 0xAC00) % 28`).
  - **Special 'ㄹ' Consonant Rule**: Accurately selects `"로"` instead of `"으로"` for words ending in 'ㄹ' 받침 (e.g., `서울로`, `칼로`, `길로`, `마을로`).
  - Numbers ending sound support (`1`, `7`, `8` end in 'ㄹ'; `0`, `3`, `6` end in general consonants; `2`, `4`, `5`, `9` without consonants).
  - English letters ending sound support (`URL로`, `RAM으로`, `API는`).
- **Comprehensive Postpositions**:
  - `은/는` (`EUN_NEUN`)
  - `이/가` (`I_GA`)
  - `을/를` (`EUL_REUL`)
  - `과/와` (`GWA_WA`)
  - `으로/로` (`EURO_RO`)
  - `이나/나` (`INA_NA`)
  - `이랑/랑` (`IRANG_RANG`)
  - `이에요/예요` (`IE_YE`)
  - `아/야` (`A_YA`)
- **Template Formatter**:
  - Automatically resolves placeholders: `JosaPicker.format("철수#{은/는} 사과#{을/를} 먹었다")` -> `"철수는 사과를 먹었다"`.

## 🚀 Usage

```java
import com.msp.josa.JosaPicker;
import com.msp.josa.JosaType;

// 1. Pick only the Josa
String josa1 = JosaPicker.pick("철수", JosaType.EUN_NEUN); // "는"
String josa2 = JosaPicker.pick("영희", JosaType.I_GA);     // "가"
String josa3 = JosaPicker.pick("선생님", JosaType.EUN_NEUN); // "은"

// 2. Special 'ㄹ' consonant handling
String seoul = JosaPicker.attach("서울", JosaType.EURO_RO); // "서울로" (O) - Not "서울으로"
String busan = JosaPicker.attach("부산", JosaType.EURO_RO); // "부산으로"

// 3. Numbers & English
String num1 = JosaPicker.attach("1", JosaType.EURO_RO);   // "1로"
String num3 = JosaPicker.attach("3", JosaType.EURO_RO);   // "3으로"
String url = JosaPicker.attach("URL", JosaType.EURO_RO);  // "URL로"

// 4. Template Formatting
String sentence = JosaPicker.format("철수#{은/는} 사과#{을/를} 먹고 서울#{으로/로} 떠났다.");
System.out.println(sentence);
// "철수는 사과를 먹고 서울로 떠났다."
```
