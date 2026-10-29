# 📦 `skill-xml-lite`

> **DOM/SAX 오버헤드나 서드파티 라이브러리 없이, 순수 Java 21 표준 라이브러리만으로 동작하는 초경량 XML 파서 및 빌더 유틸리티**

- **아티팩트 좌표**: `com.msp:skill-xml-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-xml-lite/build/libs/skill-xml-lite-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **`XmlParser.parse(String)`**:
   - 빠르고 가벼운 XML 파싱 (SAX/DOM 팩토리 복잡성 제거).
   - 속성(Attributes), 중첩 요소(Children), 텍스트 콘텐츠(Text), 자체 닫힘 태그(`<tag/>`) 완벽 지원.
   - XML 주석(`<!-- ... -->`), CDATA 블록(`<![CDATA[...]]>`), XML 선언(`<?xml ... ?>`), DOCTYPE 스킵 지원.
   - 엔티티 디코딩: `&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`, 10진수/16진수 문자 참조(`&#...;`).

2. **`XmlBuilder.element(String)`**:
   - Fluent API를 이용한 안전하고 직관적인 XML 트리 생성 및 포맷팅.
   - 엔티티 자동 이스케이프(`&`, `<`, `>`, `"`, `'`).
   - Pretty-print(`toXml(true)`) 및 압축 출력(`toXml(false)`) 지원.

3. **`XmlElement` (불변 모델)**:
   - `tag()`, `attr(name)`, `text()`, `children(tag)`, `child(tag)`, `childText(tag)` 등 편리한 탐색 메서드 제공.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.xml.XmlBuilder;
import com.msp.xml.XmlElement;
import com.msp.xml.XmlParser;

public class XmlExample {
    public static void main(String[] args) {
        // 1. Fluent Builder로 XML 생성
        String xml = XmlBuilder.element("order")
                .attr("id", "ORD-1001")
                .child(XmlBuilder.element("customer")
                        .attr("vip", "true")
                        .text("Alice Smith"))
                .child(XmlBuilder.element("item")
                        .attr("sku", "ITEM-50")
                        .attr("qty", "2")
                        .text("Mechanical Keyboard"))
                .toXml();

        System.out.println(xml);

        // 2. XML 문자열 파싱 및 트리 탐색
        XmlElement root = XmlParser.parse(xml);
        System.out.println("Order ID: " + root.attr("id"));
        System.out.println("Customer: " + root.childText("customer").orElse("N/A"));

        for (XmlElement item : root.children("item")) {
            System.out.println("Item: " + item.text() + ", Qty: " + item.attr("qty"));
        }
    }
}
```
