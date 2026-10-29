package com.msp.result.error;

/**
 * 공통적으로 자주 사용되는 표준 빌트인 에러 코드 열거형.
 */
public enum CommonErrorCode implements ErrorCode {
    SUCCESS("COMMON_200", "정상 처리되었습니다.", 200),
    BAD_REQUEST("COMMON_400", "잘못된 요청 파라미터입니다.", 400),
    UNAUTHORIZED("AUTH_401", "인증이 필요하거나 인증에 실패했습니다.", 401),
    FORBIDDEN("AUTH_403", "접근 권한이 없습니다.", 403),
    NOT_FOUND("RESOURCE_404", "요청한 리소스를 찾을 수 없습니다.", 404),
    CONFLICT("RESOURCE_409", "리소스 충돌 또는 중복이 발생했습니다.", 409),
    VALIDATION_FAILED("VALIDATION_422", "입력 데이터 유효성 검증에 실패했습니다.", 422),
    TIMEOUT("SYSTEM_408", "요청 처리 시간이 초과되었습니다.", 408),
    INTERNAL_SERVER_ERROR("SYSTEM_500", "서버 내부 처리 중 오류가 발생했습니다.", 500),
    EXTERNAL_SERVICE_ERROR("EXTERNAL_502", "외부 연동 시스템 호출에 실패했습니다.", 502);

    private final String code;
    private final String message;
    private final int httpStatus;

    CommonErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
