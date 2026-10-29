package com.msp.mockdata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MockDataGeneratorTest {

    @Test
    @DisplayName("Deterministic generator with same seed yields reproducible outputs")
    void testDeterministicReproducibility() {
        MockDataGenerator gen1 = MockDataGenerator.deterministic(12345L);
        MockDataGenerator gen2 = MockDataGenerator.deterministic(12345L);

        assertEquals(gen1.koreanName(), gen2.koreanName());
        assertEquals(gen1.englishName(), gen2.englishName());
        assertEquals(gen1.koreanMobilePhoneNumber(), gen2.koreanMobilePhoneNumber());
        assertEquals(gen1.email(), gen2.email());
        assertEquals(gen1.koreanAddress(), gen2.koreanAddress());
        assertEquals(gen1.uuid(), gen2.uuid());
        assertEquals(gen1.alphanumeric(16), gen2.alphanumeric(16));
        assertEquals(gen1.price(1000, 50000, 500), gen2.price(1000, 50000, 500));
    }

    @Test
    @DisplayName("Korean and English names have realistic formats")
    void testNames() {
        MockDataGenerator gen = new MockDataGenerator();

        for (int i = 0; i < 50; i++) {
            String krName = gen.koreanName();
            assertTrue(krName.length() >= 2 && krName.length() <= 4, "Invalid Korean name length: " + krName);

            String enName = gen.englishName();
            assertTrue(enName.contains(" "), "English name should contain first and last name separated by space: " + enName);
        }
    }

    @Test
    @DisplayName("Korean phone numbers adhere to 010-XXXX-XXXX regex")
    void testPhoneNumbers() {
        MockDataGenerator gen = new MockDataGenerator();

        for (int i = 0; i < 50; i++) {
            String mobile = gen.koreanMobilePhoneNumber();
            assertTrue(mobile.matches("^010-[2-9]\\d{3}-\\d{4}$"), "Invalid Korean mobile number: " + mobile);

            String landline = gen.koreanLandlinePhoneNumber();
            assertTrue(landline.matches("^0\\d{1,2}-\\d{3,4}-\\d{4}$"), "Invalid Korean landline number: " + landline);
        }
    }

    @Test
    @DisplayName("Email generation creates valid email address strings")
    void testEmail() {
        MockDataGenerator gen = new MockDataGenerator();

        for (int i = 0; i < 50; i++) {
            String email = gen.email();
            assertTrue(email.matches("^[a-z]+.[a-z]+\\d+@[a-z.]+$"), "Invalid email: " + email);

            String custom = gen.email("msp.internal");
            assertTrue(custom.endsWith("@msp.internal"));
        }
    }

    @Test
    @DisplayName("Korean addresses and 5-digit postal codes")
    void testAddresses() {
        MockDataGenerator gen = new MockDataGenerator();

        for (int i = 0; i < 50; i++) {
            String postal = gen.koreanPostalCode();
            assertEquals(5, postal.length());
            assertTrue(postal.matches("^\\d{5}$"));

            String address = gen.koreanAddress();
            assertFalse(address.isBlank());
            assertTrue(address.contains("로") || address.contains("길") || address.contains("대로"));
        }
    }

    @Test
    @DisplayName("Company name generation")
    void testCompanyNames() {
        MockDataGenerator gen = new MockDataGenerator();

        String krComp = gen.koreanCompanyName();
        assertTrue(krComp.contains("주식회사") || krComp.contains("(주)"));

        String enComp = gen.englishCompanyName();
        assertFalse(enComp.isBlank());
    }

    @Test
    @DisplayName("Date and time generation within specified ranges")
    void testDatesAndTimes() {
        MockDataGenerator gen = new MockDataGenerator();

        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        for (int i = 0; i < 50; i++) {
            LocalDate d = gen.date(start, end);
            assertFalse(d.isBefore(start));
            assertFalse(d.isAfter(end));
        }

        LocalDate recent = gen.recentDate(30);
        assertTrue(recent.isAfter(LocalDate.now().minusDays(31)));
        assertFalse(recent.isAfter(LocalDate.now()));

        LocalDateTime dtStart = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime dtEnd = LocalDateTime.of(2026, 1, 2, 0, 0);
        LocalDateTime dt = gen.dateTime(dtStart, dtEnd);
        assertFalse(dt.isBefore(dtStart));
        assertFalse(dt.isAfter(dtEnd));
    }

    @Test
    @DisplayName("Prices conform to min, max, and step increments")
    void testPrices() {
        MockDataGenerator gen = new MockDataGenerator();

        long min = 1000;
        long max = 10000;
        long step = 500;

        for (int i = 0; i < 50; i++) {
            long p = gen.price(min, max, step);
            assertTrue(p >= min && p <= max);
            assertEquals(0, (p - min) % step);
        }
    }

    @Test
    @DisplayName("UUID and alphanumeric tokens")
    void testTokensAndUuids() {
        MockDataGenerator gen = new MockDataGenerator();

        UUID uuid = gen.uuid();
        assertEquals(4, uuid.version());

        String alpha = gen.alphanumeric(32);
        assertEquals(32, alpha.length());
        assertTrue(alpha.matches("^[a-zA-Z0-9]{32}$"));

        String num = gen.numeric(8);
        assertEquals(8, num.length());
        assertTrue(num.matches("^\\d{8}$"));
    }

    @Test
    @DisplayName("Pick one and pick multiple collection helpers")
    void testPickHelpers() {
        MockDataGenerator gen = new MockDataGenerator();
        List<String> items = List.of("apple", "banana", "cherry", "date", "fig");

        String picked = gen.pickOne(items);
        assertTrue(items.contains(picked));

        List<String> multiple = gen.pickMultiple(items, 3);
        assertEquals(3, multiple.size());
        assertTrue(items.containsAll(multiple));
    }
}
