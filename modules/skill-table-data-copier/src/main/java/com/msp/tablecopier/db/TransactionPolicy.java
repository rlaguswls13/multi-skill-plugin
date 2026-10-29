package com.msp.tablecopier.db;

/**
 * DB Insert(Copy) 중 에러 발생 시 트랜잭션 처리 정책
 */
public enum TransactionPolicy {
    /**
     * 단 1건의 행이라도 INSERT 실패 시 전체 롤백 (All-or-Nothing)
     */
    ALL_OR_NOTHING,
    /**
     * 에러가 발생한 행은 건너뛰어 에러 리포트(CopyErrorDetail)에 기록하고,
     * 나머지 정상 행은 계속 INSERT 및 커밋 수행
     */
    CONTINUE_ON_ERROR
}
