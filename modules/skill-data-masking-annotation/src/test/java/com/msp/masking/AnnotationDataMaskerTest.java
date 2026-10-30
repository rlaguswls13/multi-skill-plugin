package com.msp.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationDataMaskerTest {

    static class UserDto {
        private String id;

        @Mask(type = MaskType.NAME)
        private String name;

        @Mask(type = MaskType.PHONE)
        private String phone;

        @Mask(type = MaskType.EMAIL)
        private String email;

        @Mask(type = MaskType.CARD)
        private String card;

        public UserDto(String id, String name, String phone, String email, String card) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.email = email;
            this.card = card;
        }
    }

    record UserRecord(
            String id,
            @Mask(type = MaskType.NAME) String name,
            @Mask(type = MaskType.PHONE) String phone
    ) {}

    @Test
    @DisplayName("Mask class fields with annotations")
    void testMaskClass() {
        UserDto dto = new UserDto("U100", "홍길동", "010-1234-5678", "tester@example.com", "1234-5678-9012-3456");
        Map<String, Object> map = AnnotationDataMasker.toMaskedMap(dto);

        assertEquals("U100", map.get("id")); // unmasked
        assertEquals("홍*동", map.get("name"));
        assertEquals("010-****-5678", map.get("phone"));
        assertEquals("t*****@example.com", map.get("email"));
        assertEquals("1234-****-****-3456", map.get("card"));

        String maskedStr = AnnotationDataMasker.toMaskedString(dto);
        assertTrue(maskedStr.contains("name=홍*동"));
        assertTrue(maskedStr.contains("phone=010-****-5678"));
    }

    @Test
    @DisplayName("Mask Java 21 Record components with annotations")
    void testMaskRecord() {
        UserRecord rec = new UserRecord("REC-1", "이순신", "010-9876-5432");
        Map<String, Object> map = AnnotationDataMasker.toMaskedMap(rec);

        assertEquals("REC-1", map.get("id"));
        assertEquals("이*신", map.get("name"));
        assertEquals("010-****-5432", map.get("phone"));
    }
}
