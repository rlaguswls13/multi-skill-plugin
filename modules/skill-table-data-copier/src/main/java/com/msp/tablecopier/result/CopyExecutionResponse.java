package com.msp.tablecopier.result;

import java.util.List;

/**
 * [고려사항 (3)] 테이블 데이터 치환 및 DB Insert(Copy) 실행 완료 후 리턴되는 결과 DTO.
 *
 * @param targetTable  INSERT 대상 테이블명
 * @param totalRows    원본 전체 데이터 행 수
 * @param insertedRows 정상적으로 DB에 INSERT(Copy)된 행 수
 * @param failedRows   에러로 인해 실패한 행 수
 * @param skippedRows  롤백 또는 중단으로 인해 미처리된 행 수
 * @param elapsedMs    총 소요 시간 (밀리초)
 * @param preparedSql  실행에 사용된 PreparedStatement SQL 템플릿
 * @param errors       발생한 에러 상세 목록 (성공 시 빈 리스트)
 */
public record CopyExecutionResponse(
        String targetTable,
        int totalRows,
        int insertedRows,
        int failedRows,
        int skippedRows,
        long elapsedMs,
        String preparedSql,
        List<CopyErrorDetail> errors
) {
}
