package com.msp.tablecopier.result;

import java.util.Map;

/**
 * [고려사항 (3)] DB Insert 또는 변환 중 에러가 발생했을 때 리턴받는 상세 에러 정보.
 *
 * @param rowNumber        에러가 발생한 행 번호 (1-based, DB 접속 실패 등 전역 오류는 0)
 * @param errorCategory    에러 분류 카테고리
 * @param targetColumn     문제가 발생한 타겟 컬럼명 (특정 불가 시 null)
 * @param rejectedValue    에러를 유발한 입력 값 (특정 불가 시 null)
 * @param sqlState         JDBC SQLState 코드 (예: "23505", "08001")
 * @param vendorErrorCode  DB 벤더 고유 에러 코드
 * @param userMessage      화면/사용자에게 안내할 한글 요약 메시지
 * @param technicalMessage 디버깅 및 로그 기록용 원시 예외 메시지
 * @param rowSnapshot      실패한 해당 행의 치환 데이터 전체 스냅샷 (재처리/다운로드 용도)
 */
public record CopyErrorDetail(
        int rowNumber,
        ErrorCategory errorCategory,
        String targetColumn,
        Object rejectedValue,
        String sqlState,
        int vendorErrorCode,
        String userMessage,
        String technicalMessage,
        Map<String, Object> rowSnapshot
) {
}
