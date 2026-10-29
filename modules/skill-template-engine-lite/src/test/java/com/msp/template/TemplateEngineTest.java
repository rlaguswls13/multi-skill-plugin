package com.msp.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TemplateEngineTest {

    record Address(String city, String zipCode) {}
    record User(String name, String email, Address address, boolean vip) {}
    record Product(String name, double price) {}

    @Test
    @DisplayName("Should interpolate simple variables and ignore missing ones")
    void testSimpleVariables() {
        String template = "Hello, ${name}! Welcome to ${platform}. Missing: '${notSet}'";
        Map<String, Object> ctx = Map.of("name", "Alice", "platform", "MSP");

        String result = Templates.render(template, ctx);
        assertEquals("Hello, Alice! Welcome to MSP. Missing: ''", result);
    }

    @Test
    @DisplayName("Should handle escaped variable expressions")
    void testEscaping() {
        String template = "Use \\${name} to display name, result: ${name}";
        String result = Templates.render(template, Map.of("name", "Bob"));
        assertEquals("Use ${name} to display name, result: Bob", result);
    }

    @Test
    @DisplayName("Should resolve nested properties from Java Records and Maps")
    void testNestedProperties() {
        User user = new User("Charlie", "charlie@example.com", new Address("Seoul", "04524"), true);
        String template = "User: ${user.name}, Email: ${user.email}, City: ${user.address.city}, Zip: ${user.address.zipCode}";

        String result = Templates.render(template, Map.of("user", user));
        assertEquals("User: Charlie, Email: charlie@example.com, City: Seoul, Zip: 04524", result);

        // Also test root object directly
        String directResult = Templates.render("Direct: ${name} in ${address.city}", user);
        assertEquals("Direct: Charlie in Seoul", directResult);
    }

    @Test
    @DisplayName("Should support default value fallback when variable is null or missing")
    void testDefaultValueFallback() {
        String template = "Hello, ${title:Guest}! Role: ${role:User}. Real: ${name:Anonymous}";
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("name", "David");
        ctx.put("role", null);

        String result = Templates.render(template, ctx);
        assertEquals("Hello, Guest! Role: User. Real: David", result);
    }

    @Test
    @DisplayName("Should format numbers and dates accurately")
    void testNumberAndDateFormatting() {
        LocalDate date = LocalDate.of(2026, Month.OCTOBER, 29);
        LocalDateTime dateTime = LocalDateTime.of(2026, Month.OCTOBER, 29, 14, 30, 0);
        long amount = 1250000;
        double ratio = 0.852;

        String template = "Amount: ${amount:#,###} won, Ratio: ${ratio:0.0%}, Created: ${createdAt:yyyy-MM-dd}, Time: ${timestamp:HH:mm:ss}";
        Map<String, Object> ctx = Map.of(
                "amount", amount,
                "ratio", ratio,
                "createdAt", date,
                "timestamp", dateTime
        );

        String result = Templates.render(template, ctx);
        assertEquals("Amount: 1,250,000 won, Ratio: 85.2%, Created: 2026-10-29, Time: 14:30:00", result);
    }

    @Test
    @DisplayName("Should evaluate simple and inverted conditional blocks")
    void testConditionalBlocks() {
        String template = "${if:isVip}VIP Member${else}Standard${endif} | ${if:!isVip}Not VIP${else}Is VIP${endif}";

        assertEquals("VIP Member | Is VIP", Templates.render(template, Map.of("isVip", true)));
        assertEquals("Standard | Not VIP", Templates.render(template, Map.of("isVip", false)));
        assertEquals("Standard | Not VIP", Templates.render(template, Collections.emptyMap()));
    }

    @Test
    @DisplayName("Should handle nested conditional blocks")
    void testNestedConditionals() {
        String template = "${if:user}${if:user.vip}VIP: ${user.name}${else}Basic: ${user.name}${endif}${else}No User${endif}";

        User vip = new User("Alice", "a@test.com", null, true);
        User basic = new User("Bob", "b@test.com", null, false);

        assertEquals("VIP: Alice", Templates.render(template, Map.of("user", vip)));
        assertEquals("Basic: Bob", Templates.render(template, Map.of("user", basic)));
        assertEquals("No User", Templates.render(template, Collections.emptyMap()));
    }

    @Test
    @DisplayName("Should iterate collections with loop metadata and current object properties")
    void testLoopIteration() {
        List<Product> products = List.of(
                new Product("Apple", 1000),
                new Product("Banana", 1500),
                new Product("Cherry", 3000)
        );

        String template = "${each:products}[${@count}/${items_total}] ${name}: ${price:#,###}${if:!@last}, ${endif}${endeach}";
        Map<String, Object> ctx = Map.of(
                "products", products,
                "items_total", products.size()
        );

        String result = Templates.render(template, ctx);
        assertEquals("[1/3] Apple: 1,000, [2/3] Banana: 1,500, [3/3] Cherry: 3,000", result);
    }

    @Test
    @DisplayName("Should iterate with alias and primitive arrays/lists")
    void testLoopWithAliasAndPrimitives() {
        List<String> tags = List.of("java", "gradle", "template");
        String template = "${each:tags as tag}#${tag}${if:!@last} ${endif}${endeach}";

        String result = Templates.render(template, Map.of("tags", tags));
        assertEquals("#java #gradle #template", result);
    }

    @Test
    @DisplayName("Should throw TemplateException on unclosed or malformed tags")
    void testMalformedTemplates() {
        assertThrows(TemplateException.class, () -> Templates.render("Unclosed: ${user.name", Collections.emptyMap()));
        assertThrows(TemplateException.class, () -> Templates.render("${if:flag}Hello", Collections.emptyMap()));
        assertThrows(TemplateException.class, () -> Templates.render("Hello${endif}", Collections.emptyMap()));
        assertThrows(TemplateException.class, () -> Templates.render("${each:list}Item", Collections.emptyMap()));
    }

    @Test
    @DisplayName("CompiledTemplate should be reusable and thread-safe")
    void testCompiledTemplateReuse() {
        CompiledTemplate compiled = Templates.compile("Greeting: ${message} to ${name}!");
        assertEquals("Greeting: Hi to Alice!", compiled.render(Map.of("message", "Hi", "name", "Alice")));
        assertEquals("Greeting: Hello to Bob!", compiled.render(Map.of("message", "Hello", "name", "Bob")));
    }
}
