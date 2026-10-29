package com.msp.sanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PiiMasker Unit Tests")
class PiiMaskerTest {

    @Nested
    @DisplayName("Resident Registration Number (RRN) Masking")
    class RrnTests {

        @Test
        @DisplayName("Standard hyphenated RRN with keepGenderDigit=true")
        void maskRrnStandard() {
            String input = "홍길동 주민번호: 950101-1234567 입니다.";
            String masked = PiiMasker.maskRrn(input);
            assertEquals("홍길동 주민번호: 950101-1****** 입니다.", masked);
        }

        @Test
        @DisplayName("Standard hyphenated RRN with keepGenderDigit=false")
        void maskRrnWithoutGender() {
            String input = "주민번호 950101-2234567";
            String masked = PiiMasker.maskRrn(input, '#', false);
            assertEquals("주민번호 950101-#######", masked);
        }

        @Test
        @DisplayName("Plain 13-digit RRN without hyphen")
        void maskRrnPlainDigits() {
            String input = "RRN 9912311234567 is here";
            String masked = PiiMasker.maskRrn(input);
            assertEquals("RRN 9912311****** is here", masked);
        }

        @Test
        @DisplayName("Null or empty handling")
        void nullOrEmpty() {
            assertNull(PiiMasker.maskRrn(null));
            assertEquals("", PiiMasker.maskRrn(""));
        }
    }

    @Nested
    @DisplayName("Credit Card Masking")
    class CreditCardTests {

        @Test
        @DisplayName("Hyphen-separated 16-digit card")
        void maskFormattedCardHyphen() {
            String input = "결제 카드: 1234-5678-9012-3456";
            String masked = PiiMasker.maskCreditCard(input);
            assertEquals("결제 카드: 1234-****-****-3456", masked);
        }

        @Test
        @DisplayName("Space-separated 16-digit card")
        void maskFormattedCardSpace() {
            String input = "Card 9876 5432 1098 7654";
            String masked = PiiMasker.maskCreditCard(input, 'X');
            assertEquals("Card 9876 XXXX XXXX 7654", masked);
        }

        @Test
        @DisplayName("Plain continuous 16-digit card")
        void maskPlain16DigitCard() {
            String input = "Raw card 1234567890123456 test";
            String masked = PiiMasker.maskCreditCard(input);
            assertEquals("Raw card 1234********3456 test", masked);
        }

        @Test
        @DisplayName("Null or empty handling")
        void nullOrEmpty() {
            assertNull(PiiMasker.maskCreditCard(null));
            assertEquals("", PiiMasker.maskCreditCard(""));
        }
    }

    @Nested
    @DisplayName("Phone Number Masking")
    class PhoneTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "010-1234-5678",
                "010 1234 5678",
                "011-987-6543",
                "02-765-4321",
                "02-1234-5678",
                "031-999-8888",
                "070-1234-5678"
        })
        @DisplayName("Diverse phone formats masked in middle part")
        void maskPhoneFormats(String phone) {
            String masked = PiiMasker.maskPhone("연락처: " + phone);
            assertFalse(masked.contains("1234") && masked.contains("5678") && phone.contains("1234"));
            assertTrue(masked.contains("*"));
        }

        @Test
        @DisplayName("Plain continuous mobile number")
        void maskPlainMobile() {
            String input = "Tel: 01012345678";
            String masked = PiiMasker.maskPhone(input);
            assertEquals("Tel: 010****5678", masked);
        }

        @Test
        @DisplayName("Null or empty handling")
        void nullOrEmpty() {
            assertNull(PiiMasker.maskPhone(null));
            assertEquals("", PiiMasker.maskPhone(""));
        }
    }

    @Nested
    @DisplayName("Email Masking")
    class EmailTests {

        @Test
        @DisplayName("Standard email masking")
        void maskStandardEmail() {
            String input = "Contact: john.doe@example.com and alice@work.org";
            String masked = PiiMasker.maskEmail(input);
            assertEquals("Contact: jo******@example.com and al***@work.org", masked);
        }

        @Test
        @DisplayName("Short username emails")
        void maskShortEmail() {
            assertEquals("*@domain.com", PiiMasker.maskEmail("a@domain.com"));
            assertEquals("a*@domain.com", PiiMasker.maskEmail("ab@domain.com"));
        }

        @Test
        @DisplayName("Custom mask character and prefix length")
        void customPrefixLength() {
            String input = "admin@example.com";
            String masked = PiiMasker.maskEmail(input, '#', 1);
            assertEquals("a####@example.com", masked);
        }
    }

    @Nested
    @DisplayName("Mask All Combined")
    class MaskAllTests {

        @Test
        @DisplayName("All PII categories in one string")
        void maskAllPii() {
            String document = """
                    고객 성명: 홍길동
                    주민등록번호: 880505-1098765
                    전화번호: 010-9876-5432
                    카드번호: 1111-2222-3333-4444
                    이메일: hong@company.co.kr
                    """;

            String result = PiiMasker.maskAll(document);
            assertFalse(result.contains("1098765"));
            assertTrue(result.contains("880505-1******"));

            assertFalse(result.contains("9876"));
            assertTrue(result.contains("010-****-5432"));

            assertFalse(result.contains("2222-3333"));
            assertTrue(result.contains("1111-****-****-4444"));

            assertFalse(result.contains("hong@"));
            assertTrue(result.contains("ho**@company.co.kr"));
        }
    }
}
