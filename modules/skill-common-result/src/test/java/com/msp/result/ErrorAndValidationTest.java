package com.msp.result;

import com.msp.result.api.ApiEnvelope;
import com.msp.result.error.CommonErrorCode;
import com.msp.result.error.CustomErrorCode;
import com.msp.result.error.ErrorCode;
import com.msp.result.validation.ValidationError;
import com.msp.result.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorAndValidationTest {

    public record SampleUser(String username, int age) {}

    @Test
    @DisplayName("ErrorCode 및 CustomErrorCode 동작 검증")
    void testErrorCode() {
        ErrorCode badRequest = CommonErrorCode.BAD_REQUEST;
        assertEquals("COMMON_400", badRequest.code());
        assertEquals(400, badRequest.httpStatus());

        ErrorCode custom = CustomErrorCode.of("ORDER_LIMIT", "주문 한도 초과", 422);
        assertEquals("ORDER_LIMIT", custom.code());
        assertEquals("주문 한도 초과", custom.message());
        assertEquals(422, custom.httpStatus());
    }

    @Test
    @DisplayName("ValidationResult 다중 검증 오류 누적(Accumulation) 및 toResult 검증")
    void testValidationResultAccumulation() {
        SampleUser invalidUser = new SampleUser("", 15);

        ValidationResult<SampleUser> valResult = ValidationResult.of(invalidUser)
                .checkNotEmpty(invalidUser.username(), "username", "사용자명은 필수입니다.")
                .check(u -> u.age() >= 19, "age", invalidUser.age(), "19세 이상만 가능합니다.")
                .build();

        assertTrue(valResult.isInvalid());
        assertFalse(valResult.isValid());
        assertEquals(2, valResult.errors().size());

        ValidationError err1 = valResult.errors().get(0);
        assertEquals("username", err1.fieldName());

        ValidationError err2 = valResult.errors().get(1);
        assertEquals("age", err2.fieldName());
        assertEquals(15, err2.rejectedValue());

        // toResult
        Result<SampleUser, List<ValidationError>> res = valResult.toResult();
        assertTrue(res.isFailure());
        assertEquals(2, res.getErrorOrNull().size());
    }

    @Test
    @DisplayName("ApiEnvelope 성공/실패 래핑 및 순수 Java .toJson() 직렬화 검증")
    void testApiEnvelopeToJson() {
        ApiEnvelope<SampleUser> okEnv = ApiEnvelope.ok(new SampleUser("alice", 25));
        assertTrue(okEnv.success());
        assertEquals(200, okEnv.status());
        assertEquals("COMMON_200", okEnv.code());

        String json = okEnv.toJson();
        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"username\":\"alice\""));
        assertTrue(json.contains("\"age\":25"));

        // 에러 엔벨로프
        ApiEnvelope<Void> errEnv = ApiEnvelope.error(CommonErrorCode.NOT_FOUND, "ID=999");
        assertFalse(errEnv.success());
        assertEquals(404, errEnv.status());
        String errJson = errEnv.toJson();
        assertTrue(errJson.contains("\"success\":false"));
        assertTrue(errJson.contains("\"code\":\"RESOURCE_404\""));
        assertTrue(errJson.contains("\"error\":\"ID=999\""));
    }
}
