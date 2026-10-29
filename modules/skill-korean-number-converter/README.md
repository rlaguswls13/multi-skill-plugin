# 🇰🇷 Skill: Korean Number Converter (`com.msp.hangulnum`)

Converts numeric values and currencies into Korean spoken words, formal receipt typography ("금 일천이백삼십사만 오천 원정"), fraud-resistant legal Hanja ("金 壹萬... 圓整"), and native pure Korean counting expressions ("한 개, 두 개..."). Pure Java 21 standard library with zero runtime dependencies.

---

## 🌟 Key Features

- **Spoken Korean Numbers**: Natural 4-digit block grouping ("일만 이천삼백사십오", "일조 이천억").
- **Formal Currency Receipt Typography**: Automatic formal receipt style ("금 ... 원정") with explicit "일/壹" prefix for units to prevent forgery.
- **Hanja Formal (갖은자/대문자)**: Fraud-resistant characters (`壹, 貳, 參, 肆, 伍, 陸, 漆, 捌, 玖, 拾, 佰, 阡, 萬...`).
- **Native Pure Korean Count & Modifiers**: Handles inflections before unit nouns (`하나` -> `한 개`, `둘` -> `두 명`, `스물` -> `스무 개`, `스물한 개`).

---

## 🚀 Quick Start

### 1. Spoken Korean Numbers & Currency Receipts

```java
import com.msp.hangulnum.KoreanNumberConverter;

// Spoken reading
String text = KoreanNumberConverter.toHangul(12345);
// Output: "일만 이천삼백사십오"

// Formal receipt amount
String receipt = KoreanNumberConverter.toCurrency(12345000L);
// Output: "금 일천이백삼십사만 오천 원정"
```

### 2. Legal / Financial Hanja (대문자 갖은자)

```java
import com.msp.hangulnum.KoreanNumberConverter;

String hanja = KoreanNumberConverter.toHanjaFormal(12345L);
// Output: "金 壹萬 貳阡參佰肆拾伍 圓整"
```

### 3. Native Korean Counting with Unit Nouns

```java
import com.msp.hangulnum.KoreanNumberConverter;

// Pure cardinal counting
String count = KoreanNumberConverter.toPureKoreanCount(23);
// Output: "스물셋"

// Inflected numeral modifier with noun
String items = KoreanNumberConverter.toPureKoreanCount(1, "개");
// Output: "한 개"

String people = KoreanNumberConverter.toPureKoreanCount(20, "명");
// Output: "스무 명"
```
