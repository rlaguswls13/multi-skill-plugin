package com.msp.tablecopier.result;

/**
 * 에러 원인 분류 카테고리 (호출 측에서 에러 유형별 분기 처리 가능)
 */
public enum ErrorCategory {
    /** DB 접속 실패, 계정/암호 오류, 드라이버 미존재, Access 파일 경로 오류 */
    DB_CONNECTION_ERROR,
    /** 존재하지 않는 테이블/컬럼명 또는 SQL 구문 오류 */
    SQL_SYNTAX_OR_SCHEMA_ERROR,
    /** PK 중복, NOT NULL 위반, 데이터 길이 초과, 타입 불일치 등 제약조건 오류 */
    DATA_CONSTRAINT_ERROR,
    /** 컬럼 매핑 규칙 누락 또는 원본 헤더 매칭 실패 */
    MAPPING_TRANSFORM_ERROR,
    /** 엑셀/텍스트 파일 읽기 및 파싱 오류 */
    FILE_PARSE_ERROR
}
