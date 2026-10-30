package com.msp.anonymizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnonymizerTest {

    @Test
    @DisplayName("Age generalization test")
    void testGeneralizeAge() {
        assertEquals("10대 미만", Anonymizer.generalizeAge(7));
        assertEquals("20대", Anonymizer.generalizeAge(24));
        assertEquals("30대", Anonymizer.generalizeAge(39));
        assertEquals("50대", Anonymizer.generalizeAge(50));
    }

    @Test
    @DisplayName("Address generalization test")
    void testGeneralizeAddress() {
        assertEquals("서울특별시 강남구", Anonymizer.generalizeAddress("서울특별시 강남구 역삼동 123-45 테헤란로"));
        assertEquals("경기도 성남시", Anonymizer.generalizeAddress("경기도 성남시 분당구 판교역로"));
    }

    @Test
    @DisplayName("Pseudonymization test with HMAC salt")
    void testPseudonymize() {
        String token1 = Anonymizer.pseudonymize("user123", "secret-salt");
        String token2 = Anonymizer.pseudonymize("user123", "secret-salt");
        String tokenOther = Anonymizer.pseudonymize("user999", "secret-salt");

        assertNotNull(token1);
        assertEquals(64, token1.length());
        assertEquals(token1, token2);
        assertNotEquals(token1, tokenOther);
    }

    @Test
    @DisplayName("Perturbation noise bounds test")
    void testPerturb() {
        double original = 100.0;
        double noiseRange = 5.0;
        for (int i = 0; i < 20; i++) {
            double perturbed = Anonymizer.perturb(original, noiseRange);
            assertTrue(perturbed >= 95.0 && perturbed <= 105.0);
        }
    }

    @Test
    @DisplayName("General masking test")
    void testMask() {
        assertEquals("홍*동", Anonymizer.mask("홍길동", 1, 1, '*'));
        assertEquals("010-****-5678", Anonymizer.mask("010-1234-5678", 4, 5, '*'));
    }
}
