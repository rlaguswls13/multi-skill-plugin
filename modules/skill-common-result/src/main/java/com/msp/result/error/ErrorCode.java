package com.msp.result.error;

/**
 * 시스템 전반에서 사용할 수 있는 표준 에러 코드 규격 인터페이스.
 */
public interface ErrorCode {

    String code();

    String message();

    int httpStatus();
}
