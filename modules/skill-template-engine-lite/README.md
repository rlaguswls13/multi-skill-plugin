# 📦 `skill-template-engine-lite`

> **가볍고 빠르며 외부 의존성이 전혀 없는 Java 21 템플릿 엔진 (`TemplateEngine`, `Templates`)**

- **아티팩트 좌표**: `com.msp:skill-template-engine-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-template-engine-lite/build/libs/skill-template-engine-lite-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리)

---

## 1. 주요 제공 기능 (Features)

1. **변수 보간 (Variable Interpolation)**: `${name}`, `${user.email}` (Record, POJO, Map 중첩 경로 지원)
2. **기본값 대체 (Default Value Fallback)**: `${title:Guest}`, `${role:User}`
3. **숫자 및 날짜 포맷팅**:
   - 숫자 포맷: `${amount:#,###}`, `${ratio:0.0%}`
   - 날짜 포맷: `${createdAt:yyyy-MM-dd}`, `${time:HH:mm:ss}` (`LocalDate`, `LocalDateTime`, `Instant`, `Date` 지원)
4. **조건문 블록 (Conditionals)**:
   - `${if:isVip}VIP Member${else}Standard${endif}`
   - 부정 조건: `${if:!isVip}Standard${endif}`
   - 중첩 조건문 지원
5. **반복문 블록 (Loops)**:
   - `${each:items}Item: ${name}\n${endeach}`
   - 별칭 지정: `${each:items as item}${item.title}${endeach}`
   - 루프 메타데이터 지원: `${@index}`, `${@count}`, `${@first}`, `${@last}`, `${this}`
6. **사전 컴파일 캐싱 (Pre-compilation)**: `Templates.compile(template)`로 파싱 트리를 재사용하여 고성능 렌더링 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.template.Templates;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class TemplateExample {

    record Product(String name, int price) {}
    record Order(String orderId, String customer, List<Product> items, LocalDate orderDate, boolean vip) {}

    public static void main(String[] args) {
        Order order = new Order(
            "ORD-2026-001",
            "Alice",
            List.of(new Product("Keyboard", 89000), new Product("Mouse", 45000)),
            LocalDate.now(),
            true
        );

        String template = """
            ========================================
            주문 번호: ${orderId} (${createdAt:yyyy-MM-dd})
            고객명: ${customer:Guest} (${if:vip}VIP 회원${else}일반 회원${endif})
            ----------------------------------------
            [주문 내역]
            ${each:items}  ${@count}. ${name} - ${price:#,###}원
            ${endeach}========================================
            """;

        String output = Templates.render(template, order);
        System.out.println(output);
    }
}
```
