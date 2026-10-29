package com.msp.tablecopier.result;

/**
 * 데이터 치환 및 DB Copy(Insert) 실행의 최종 상태 코드
 */
public enum CopyStatus {
    /** 전체 행 정상 처리 완료 */
    SUCCESS,
    /** 일부 행 성공 및 일부 행 에러 발생 (CONTINUE_ON_ERROR 정책 시) */
    PARTIAL_SUCCESS,
    /** 전체 실패 (DB 접속 실패, 파일 파싱 오류, 또는 ALL_OR_NOTHING 롤백) */
    FAILED
}
